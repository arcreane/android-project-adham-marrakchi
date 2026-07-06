<?php
declare(strict_types=1);

namespace QuizArena\Controllers;

use QuizArena\Clock;
use QuizArena\Config;
use QuizArena\Database;
use QuizArena\Engine;
use QuizArena\Http;

final class GameController
{
    /** GET /games — F-03 : salles ouvertes du lobby. */
    public function index(): never
    {
        $rows = Database::all(
            'SELECT g.id, g.difficulty, g.max_players, g.rounds_total, g.created_at,
                    p.pseudo AS host_pseudo, c.id AS category_id, c.name AS category_name,
                    (SELECT COUNT(*) FROM game_players gp WHERE gp.game_id = g.id) AS players_count
             FROM games g
             JOIN players p ON p.id = g.host_id
             JOIN categories c ON c.id = g.category_id
             WHERE g.status = \'waiting\'
             ORDER BY g.created_at DESC'
        );
        Http::json(array_map(static fn (array $r): array => [
            'id'           => (int) $r['id'],
            'hostPseudo'   => $r['host_pseudo'],
            'categoryId'   => (int) $r['category_id'],
            'categoryName' => $r['category_name'],
            'difficulty'   => $r['difficulty'],
            'playersCount' => (int) $r['players_count'],
            'maxPlayers'   => (int) $r['max_players'],
            'roundsTotal'  => (int) $r['rounds_total'],
            'createdAt'    => Clock::dbToIso($r['created_at']),
        ], $rows));
    }

    /** POST /games — F-04 : créer une salle. */
    public function create(array $player): never
    {
        $body = Http::body();
        $categoryId = (int) Http::require($body, 'categoryId');
        $difficulty = (string) Http::require($body, 'difficulty');
        $maxPlayers = (int) ($body['maxPlayers'] ?? 8);
        $rounds = (int) ($body['rounds'] ?? Config::get('game.rounds_default', 10));

        if (!in_array($difficulty, ['easy', 'medium', 'hard', 'mixed'], true)) {
            Http::fail(400, 'INVALID_DIFFICULTY', 'Difficulté invalide (easy, medium, hard ou mixed).');
        }
        if ($maxPlayers < 2 || $maxPlayers > 8) {
            Http::fail(400, 'INVALID_CAPACITY', 'La capacité doit être comprise entre 2 et 8 joueurs.');
        }
        if ($rounds < 3 || $rounds > 10) {
            Http::fail(400, 'INVALID_ROUNDS', 'Le nombre de manches doit être compris entre 3 et 10.');
        }
        if (Database::one('SELECT id FROM categories WHERE id = ? AND active = 1', [$categoryId]) === null) {
            Http::fail(404, 'CATEGORY_NOT_FOUND', 'Catégorie inconnue.');
        }
        $this->rejectIfBusy($player['id']);

        $now = Clock::toDb(Clock::nowMs());
        $gameId = Database::transaction(function () use ($player, $categoryId, $difficulty, $maxPlayers, $rounds, $now): int {
            Database::run(
                'INSERT INTO games (host_id, category_id, difficulty, status, max_players, rounds_total, created_at)
                 VALUES (?, ?, ?, \'waiting\', ?, ?, ?)',
                [$player['id'], $categoryId, $difficulty, $maxPlayers, $rounds, $now]
            );
            $gameId = (int) Database::pdo()->lastInsertId();
            Database::run(
                'INSERT INTO game_players (game_id, player_id, ready, joined_at) VALUES (?, ?, 1, ?)',
                [$gameId, $player['id'], $now]
            );
            return $gameId;
        });

        Http::json($this->buildState($gameId, $player['id']), 201);
    }

    /** POST /games/{id}/join — F-05. */
    public function join(array $player, int $gameId): never
    {
        $this->rejectIfBusy($player['id'], $gameId);

        Database::transaction(function () use ($player, $gameId): void {
            $game = Database::one('SELECT * FROM games WHERE id = ? FOR UPDATE', [$gameId]);
            if ($game === null) {
                Http::fail(404, 'GAME_NOT_FOUND', 'Salle introuvable.');
            }
            if ($game['status'] !== 'waiting') {
                Http::fail(409, 'GAME_ALREADY_STARTED', 'La partie a déjà commencé.');
            }
            $already = Database::one(
                'SELECT 1 FROM game_players WHERE game_id = ? AND player_id = ?',
                [$gameId, $player['id']]
            );
            if ($already !== null) {
                return; // déjà dans la salle : idempotent
            }
            $count = (int) Database::scalar(
                'SELECT COUNT(*) FROM game_players WHERE game_id = ?',
                [$gameId]
            );
            if ($count >= (int) $game['max_players']) {
                Http::fail(409, 'GAME_FULL', 'La salle est pleine.');
            }
            Database::run(
                'INSERT INTO game_players (game_id, player_id, joined_at) VALUES (?, ?, ?)',
                [$gameId, $player['id'], Clock::toDb(Clock::nowMs())]
            );
        });

        Http::json($this->buildState($gameId, $player['id']));
    }

    /** POST /games/{id}/ready — F-05 : signaler l'état prêt. */
    public function ready(array $player, int $gameId): never
    {
        $body = Http::body();
        $ready = (bool) ($body['ready'] ?? true);

        $game = $this->loadGame($gameId);
        if ($game['status'] !== 'waiting') {
            Http::fail(409, 'GAME_ALREADY_STARTED', 'La partie a déjà commencé.');
        }
        $this->requireParticipant($gameId, $player['id']);
        Database::run(
            'UPDATE game_players SET ready = ? WHERE game_id = ? AND player_id = ?',
            [$ready ? 1 : 0, $gameId, $player['id']]
        );
        Http::json($this->buildState($gameId, $player['id']));
    }

    /** POST /games/{id}/start — F-06 : lancement réservé à l'hôte, 2 joueurs minimum. */
    public function start(array $player, int $gameId): never
    {
        Database::transaction(function () use ($player, $gameId): void {
            $game = Database::one('SELECT * FROM games WHERE id = ? FOR UPDATE', [$gameId]);
            if ($game === null) {
                Http::fail(404, 'GAME_NOT_FOUND', 'Salle introuvable.');
            }
            if ((int) $game['host_id'] !== $player['id']) {
                Http::fail(403, 'NOT_HOST', 'Seul l\'hôte peut lancer la partie.');
            }
            if ($game['status'] !== 'waiting') {
                Http::fail(409, 'GAME_ALREADY_STARTED', 'La partie a déjà commencé.');
            }
            $players = Database::all(
                'SELECT player_id, ready FROM game_players WHERE game_id = ?',
                [$gameId]
            );
            if (count($players) < 2) {
                Http::fail(409, 'NOT_ENOUGH_PLAYERS', 'Il faut au moins 2 joueurs pour lancer la partie.');
            }
            foreach ($players as $p) {
                if ((int) $p['player_id'] !== $player['id'] && !(bool) $p['ready']) {
                    Http::fail(409, 'PLAYERS_NOT_READY', 'Tous les joueurs doivent être prêts.');
                }
            }
            Database::run(
                'UPDATE games SET status = \'running\', started_at = ? WHERE id = ?',
                [Clock::toDb(Clock::nowMs()), $gameId]
            );
        });

        Engine::openRound($gameId, 1);
        Http::json($this->buildState($gameId, $player['id']));
    }

    /** POST /games/{id}/leave — F-05 : quitter la salle ou abandonner. */
    public function leave(array $player, int $gameId): never
    {
        $game = $this->loadGame($gameId);
        $this->requireParticipant($gameId, $player['id']);

        if ($game['status'] === 'waiting') {
            if ((int) $game['host_id'] === $player['id']) {
                Engine::cancelGame($gameId); // l'hôte part : la salle est annulée
            } else {
                Database::run(
                    'DELETE FROM game_players WHERE game_id = ? AND player_id = ?',
                    [$gameId, $player['id']]
                );
            }
        } elseif ($game['status'] === 'running') {
            Database::run(
                'UPDATE game_players SET status = \'left\' WHERE game_id = ? AND player_id = ? AND status <> \'left\'',
                [$gameId, $player['id']]
            );
            $active = Engine::activeCount($gameId);
            if ($active === 0) {
                Engine::cancelGame($gameId);
            } elseif ($active === 1) {
                Engine::finishGame($gameId);
            }
        }
        Http::json(['left' => true]);
    }

    /** GET /games/{id}/state — état complet pour le polling (NF-03). */
    public function state(array $player, int $gameId): never
    {
        $this->loadGame($gameId);
        $this->requireParticipant($gameId, $player['id']);
        Engine::tick($gameId);
        Http::json($this->buildState($gameId, $player['id']));
    }

    /** GET /games/{id}/question — F-07 : question courante sans la solution (NF-05). */
    public function question(array $player, int $gameId): never
    {
        $game = $this->loadGame($gameId);
        $this->requireParticipant($gameId, $player['id']);
        Engine::tick($gameId);

        $game = $this->loadGame($gameId);
        if ($game['status'] !== 'running') {
            Http::fail(409, 'GAME_NOT_RUNNING', 'La partie n\'est pas en cours.');
        }
        $round = Engine::currentRound($gameId);
        if ($round === null || $round['closed_at'] !== null) {
            Http::fail(409, 'NO_OPEN_ROUND', 'Aucune question en cours, consultez l\'état de la partie.');
        }
        $q = Database::one(
            'SELECT id, text, choices_json, image_url FROM questions WHERE id = ?',
            [$round['question_id']]
        );
        $mine = Database::one(
            'SELECT choice_index FROM answers WHERE round_id = ? AND player_id = ?',
            [$round['id'], $player['id']]
        );

        Http::json([
            'roundNumber' => (int) $round['number'],
            'questionId'  => (int) $q['id'],
            'text'        => $q['text'],
            'choices'     => json_decode($q['choices_json'], true),
            'imageUrl'    => $q['image_url'],
            'openedAt'    => Clock::dbToIso($round['opened_at']),
            'deadlineAt'  => Clock::dbToIso($round['deadline_at']),
            'serverNow'   => Clock::toIso(Clock::nowMs()),
            'answered'    => $mine !== null,
            'yourChoice'  => $mine !== null ? (int) $mine['choice_index'] : null,
        ]);
    }

    /** POST /games/{id}/answers — F-08 : une seule réponse, points côté serveur. */
    public function answer(array $player, int $gameId): never
    {
        $body = Http::body();
        $questionId = (int) Http::require($body, 'questionId');
        $choiceIndex = $body['choiceIndex'] ?? null;
        if (!is_int($choiceIndex) || $choiceIndex < 0 || $choiceIndex > 3) {
            Http::fail(400, 'INVALID_CHOICE', 'choiceIndex doit être un entier entre 0 et 3.');
        }

        $this->loadGame($gameId);
        $this->requireParticipant($gameId, $player['id']);
        Engine::tick($gameId);

        // Relecture après tick() : l'état a pu avancer (clôture, élimination).
        $game = $this->loadGame($gameId);
        $gp = $this->requireParticipant($gameId, $player['id']);

        if ($game['status'] !== 'running') {
            Http::fail(409, 'GAME_NOT_RUNNING', 'La partie n\'est pas en cours.');
        }
        if ($gp['status'] !== 'active') {
            Http::fail(403, 'PLAYER_ELIMINATED', 'Vous ne participez plus à cette manche.');
        }

        $round = Engine::currentRound($gameId);
        if ($round === null || (int) $round['question_id'] !== $questionId) {
            Http::fail(409, 'WRONG_QUESTION', 'Cette question n\'est plus la question courante.');
        }
        $now = Clock::nowMs();
        $deadline = Clock::fromDb($round['deadline_at']) ?? 0;
        if ($round['closed_at'] !== null || $now > $deadline) {
            // Règle d'équité §2.4 : une réponse envoyée après la date limite est refusée.
            Http::fail(409, 'DEADLINE_PASSED', 'Trop tard, la manche est terminée.');
        }

        $openedAt = Clock::fromDb($round['opened_at']) ?? $now;
        try {
            Database::run(
                'INSERT INTO answers (round_id, player_id, choice_index, received_at, response_ms)
                 VALUES (?, ?, ?, ?, ?)',
                [$round['id'], $player['id'], $choiceIndex, Clock::toDb($now), max(0, $now - $openedAt)]
            );
        } catch (\PDOException $e) {
            if ($e->getCode() === '23000') {
                // Contrainte unique (round_id, player_id) : double soumission -> 409.
                Http::fail(409, 'ALREADY_ANSWERED', 'Une réponse a déjà été enregistrée pour cette question.');
            }
            throw $e;
        }

        // Clôture anticipée si tous les joueurs actifs ont répondu.
        $answers = (int) Database::scalar('SELECT COUNT(*) FROM answers WHERE round_id = ?', [$round['id']]);
        if ($answers >= Engine::activeCount($gameId)) {
            Engine::closeRound((int) $round['id']);
        }

        // La correction et le score ne sont révélés qu'à la clôture de la manche.
        Http::json(['accepted' => true, 'receivedAt' => Clock::toIso($now)], 201);
    }

    /** GET /games/{id}/scores — F-09 : classement et résultat de la dernière manche close. */
    public function scores(array $player, int $gameId): never
    {
        $this->loadGame($gameId);
        $this->requireParticipant($gameId, $player['id']);
        Engine::tick($gameId);

        $lastRound = Database::one(
            'SELECT * FROM rounds WHERE game_id = ? AND closed_at IS NOT NULL ORDER BY number DESC LIMIT 1',
            [$gameId]
        );
        $correctIndex = null;
        $answersByPlayer = [];
        if ($lastRound !== null) {
            $correctIndex = (int) Database::scalar(
                'SELECT correct_index FROM questions WHERE id = ?',
                [$lastRound['question_id']]
            );
            foreach (Database::all('SELECT * FROM answers WHERE round_id = ?', [$lastRound['id']]) as $a) {
                $answersByPlayer[(int) $a['player_id']] = $a;
            }
        }

        $players = Database::all(
            'SELECT gp.player_id, gp.score, gp.status, gp.eliminated_at, gp.final_rank, p.pseudo, p.avatar_url
             FROM game_players gp JOIN players p ON p.id = gp.player_id
             WHERE gp.game_id = ?
             ORDER BY (gp.status = \'active\') DESC, gp.score DESC, p.pseudo ASC',
            [$gameId]
        );
        $ranking = [];
        foreach ($players as $i => $p) {
            $a = $answersByPlayer[(int) $p['player_id']] ?? null;
            $ranking[] = [
                'playerId'            => (int) $p['player_id'],
                'pseudo'              => $p['pseudo'],
                'avatarUrl'           => $p['avatar_url'],
                'rank'                => $p['final_rank'] !== null ? (int) $p['final_rank'] : $i + 1,
                'score'               => (int) $p['score'],
                'status'              => $p['status'],
                'lastChoice'          => $a !== null ? (int) $a['choice_index'] : null,
                'lastCorrect'         => $a !== null && $a['is_correct'] !== null ? (bool) $a['is_correct'] : null,
                'lastPoints'          => $a !== null && $a['points'] !== null ? (int) $a['points'] : null,
                'lastResponseMs'      => $a !== null ? (int) $a['response_ms'] : null,
                'eliminatedThisRound' => $lastRound !== null && $p['eliminated_at'] !== null
                    && $p['eliminated_at'] === $lastRound['closed_at'],
            ];
        }

        Http::json([
            'roundNumber'  => $lastRound !== null ? (int) $lastRound['number'] : 0,
            'correctIndex' => $correctIndex,
            'ranking'      => $ranking,
        ]);
    }

    // ------------------------------------------------------------------

    private function loadGame(int $gameId): array
    {
        $game = Database::one('SELECT * FROM games WHERE id = ?', [$gameId]);
        if ($game === null) {
            Http::fail(404, 'GAME_NOT_FOUND', 'Salle introuvable.');
        }
        return $game;
    }

    private function requireParticipant(int $gameId, int $playerId): array
    {
        $gp = Database::one(
            'SELECT * FROM game_players WHERE game_id = ? AND player_id = ?',
            [$gameId, $playerId]
        );
        if ($gp === null) {
            Http::fail(403, 'NOT_PARTICIPANT', 'Vous ne participez pas à cette partie.');
        }
        return $gp;
    }

    /** 409 si le joueur est déjà dans une autre salle ouverte ou une partie en cours. */
    private function rejectIfBusy(int $playerId, ?int $exceptGameId = null): void
    {
        $row = Database::one(
            'SELECT g.id FROM game_players gp
             JOIN games g ON g.id = gp.game_id
             WHERE gp.player_id = ? AND g.status IN (\'waiting\', \'running\') AND gp.status = \'active\''
            . ($exceptGameId !== null ? ' AND g.id <> ' . $exceptGameId : '') . ' LIMIT 1',
            [$playerId]
        );
        if ($row !== null) {
            Http::fail(409, 'ALREADY_IN_GAME',
                'Vous êtes déjà dans une partie (salle n°' . $row['id'] . '). Quittez-la d\'abord.');
        }
    }

    /** État complet de la partie tel que consommé par le client JavaFX. */
    private function buildState(int $gameId, int $viewerId): array
    {
        $game = Database::one(
            'SELECT g.*, c.name AS category_name, hp.pseudo AS host_pseudo, wp.pseudo AS winner_pseudo
             FROM games g
             JOIN categories c ON c.id = g.category_id
             JOIN players hp ON hp.id = g.host_id
             LEFT JOIN players wp ON wp.id = g.winner_id
             WHERE g.id = ?',
            [$gameId]
        );

        $round = Engine::currentRound($gameId);
        $lastClosed = Database::one(
            'SELECT * FROM rounds WHERE game_id = ? AND closed_at IS NOT NULL ORDER BY number DESC LIMIT 1',
            [$gameId]
        );

        $phase = match ($game['status']) {
            'waiting'   => 'waiting',
            'cancelled' => 'cancelled',
            'finished'  => 'finished',
            'running'   => ($round !== null && $round['closed_at'] === null) ? 'question' : 'results',
            default     => $game['status'],
        };

        $answersByPlayer = [];
        $lastRoundInfo = null;
        if ($lastClosed !== null) {
            foreach (Database::all('SELECT * FROM answers WHERE round_id = ?', [$lastClosed['id']]) as $a) {
                $answersByPlayer[(int) $a['player_id']] = $a;
            }
            $lastRoundInfo = [
                'number'       => (int) $lastClosed['number'],
                'correctIndex' => (int) Database::scalar(
                    'SELECT correct_index FROM questions WHERE id = ?',
                    [$lastClosed['question_id']]
                ),
            ];
        }

        $players = [];
        foreach (Database::all(
            'SELECT gp.*, p.pseudo, p.avatar_url FROM game_players gp
             JOIN players p ON p.id = gp.player_id
             WHERE gp.game_id = ?
             ORDER BY gp.score DESC, p.pseudo ASC',
            [$gameId]
        ) as $gp) {
            $a = $answersByPlayer[(int) $gp['player_id']] ?? null;
            $players[] = [
                'id'                  => (int) $gp['player_id'],
                'pseudo'              => $gp['pseudo'],
                'avatarUrl'           => $gp['avatar_url'],
                'score'               => (int) $gp['score'],
                'status'              => $gp['status'],
                'ready'               => (bool) $gp['ready'],
                'isHost'              => (int) $gp['player_id'] === (int) $game['host_id'],
                'finalRank'           => $gp['final_rank'] !== null ? (int) $gp['final_rank'] : null,
                'lastChoice'          => $a !== null ? (int) $a['choice_index'] : null,
                'lastCorrect'         => $a !== null && $a['is_correct'] !== null ? (bool) $a['is_correct'] : null,
                'lastPoints'          => $a !== null && $a['points'] !== null ? (int) $a['points'] : null,
                'eliminatedThisRound' => $lastClosed !== null && $gp['eliminated_at'] !== null
                    && $gp['eliminated_at'] === $lastClosed['closed_at'],
            ];
        }

        return [
            'id'           => (int) $game['id'],
            'status'       => $game['status'],
            'phase'        => $phase,
            'categoryId'   => (int) $game['category_id'],
            'categoryName' => $game['category_name'],
            'difficulty'   => $game['difficulty'],
            'maxPlayers'   => (int) $game['max_players'],
            'roundsTotal'  => (int) $game['rounds_total'],
            'roundNo'      => (int) $game['round_no'],
            'hostId'       => (int) $game['host_id'],
            'hostPseudo'   => $game['host_pseudo'],
            'youId'        => $viewerId,
            'serverNow'    => Clock::toIso(Clock::nowMs()),
            'deadlineAt'   => ($phase === 'question' && $round !== null) ? Clock::dbToIso($round['deadline_at']) : null,
            'resultsUntil' => ($phase === 'results' && $lastClosed !== null)
                ? Clock::toIso((Clock::fromDb($lastClosed['closed_at']) ?? 0) + Config::resultsMs()) : null,
            'winnerId'     => $game['winner_id'] !== null ? (int) $game['winner_id'] : null,
            'winnerPseudo' => $game['winner_pseudo'],
            'lastRound'    => $lastRoundInfo,
            'players'      => $players,
        ];
    }
}
