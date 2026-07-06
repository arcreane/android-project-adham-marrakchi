<?php
declare(strict_types=1);

namespace QuizArena;

/**
 * Moteur de partie côté serveur — source de vérité unique.
 *
 * Le POC fonctionne sans tâche de fond : chaque requête authentifiée sur une
 * partie appelle d'abord tick(), qui fait avancer l'état si nécessaire
 * (clôture de manche à l'échéance ou quand tous les actifs ont répondu,
 * ouverture de la manche suivante après la fenêtre de résultats, fin de partie).
 * Toutes les écritures sensibles sont transactionnelles et re-vérifiées
 * sous verrou pour tolérer le polling concurrent des clients.
 */
final class Engine
{
    /** Fait avancer la partie si une échéance est passée. */
    public static function tick(int $gameId): void
    {
        $game = Database::one('SELECT * FROM games WHERE id = ?', [$gameId]);
        if ($game === null || $game['status'] !== 'running') {
            return;
        }

        $round = self::currentRound($gameId);
        $now = Clock::nowMs();

        if ($round !== null && $round['closed_at'] === null) {
            $deadline = Clock::fromDb($round['deadline_at']) ?? 0;
            $answers = (int) Database::scalar('SELECT COUNT(*) FROM answers WHERE round_id = ?', [$round['id']]);
            $active = self::activeCount($gameId);
            if ($now >= $deadline || ($active > 0 && $answers >= $active)) {
                self::closeRound((int) $round['id']);
                $round = self::currentRound($gameId);
            } else {
                return; // manche en cours, rien à faire
            }
        }

        if ($round !== null && $round['closed_at'] !== null) {
            $closedMs = Clock::fromDb($round['closed_at']) ?? 0;
            if ($now < $closedMs + Config::resultsMs()) {
                return; // fenêtre d'affichage des résultats
            }
            $active = self::activeCount($gameId);
            if ($active <= 1 || (int) $round['number'] >= (int) $game['rounds_total']) {
                self::finishGame($gameId);
            } else {
                self::openRound($gameId, (int) $round['number'] + 1);
            }
        }
    }

    /** Ouvre la manche $number : même question et même échéance pour tous. */
    public static function openRound(int $gameId, int $number): void
    {
        $game = Database::one('SELECT * FROM games WHERE id = ?', [$gameId]);
        if ($game === null || $game['status'] !== 'running') {
            return;
        }

        $questionId = self::pickQuestion($gameId, (int) $game['category_id'], $game['difficulty']);
        if ($questionId === null) {
            Log::info('Plus de questions disponibles, fin anticipée', ['game' => $gameId]);
            self::finishGame($gameId);
            return;
        }

        $now = Clock::nowMs();
        try {
            Database::run(
                'INSERT INTO rounds (game_id, number, question_id, opened_at, deadline_at)
                 VALUES (?, ?, ?, ?, ?)',
                [$gameId, $number, $questionId, Clock::toDb($now), Clock::toDb($now + Config::questionMs())]
            );
        } catch (\PDOException $e) {
            if ($e->getCode() === '23000') {
                return; // un autre client vient d'ouvrir la même manche
            }
            throw $e;
        }
        Database::run('UPDATE games SET round_no = ? WHERE id = ?', [$number, $gameId]);
    }

    /**
     * Clôture d'une manche dans une transaction (§6.1) : correction des
     * réponses, calcul des points côté serveur, élimination éventuelle.
     */
    public static function closeRound(int $roundId): void
    {
        Database::transaction(function () use ($roundId): void {
            $round = Database::one('SELECT * FROM rounds WHERE id = ? FOR UPDATE', [$roundId]);
            if ($round === null || $round['closed_at'] !== null) {
                return; // déjà clôturée par une requête concurrente
            }
            $gameId = (int) $round['game_id'];
            $correctIndex = (int) Database::scalar(
                'SELECT correct_index FROM questions WHERE id = ?',
                [$round['question_id']]
            );

            $answers = Database::all('SELECT * FROM answers WHERE round_id = ?', [$roundId]);
            foreach ($answers as $answer) {
                $isCorrect = (int) $answer['choice_index'] === $correctIndex;
                $points = Score::compute($isCorrect, (int) $answer['response_ms'], Config::questionMs());
                Database::run(
                    'UPDATE answers SET is_correct = ?, points = ? WHERE id = ?',
                    [$isCorrect ? 1 : 0, $points, $answer['id']]
                );
                if ($points > 0) {
                    Database::run(
                        'UPDATE game_players SET score = score + ? WHERE game_id = ? AND player_id = ?',
                        [$points, $gameId, $answer['player_id']]
                    );
                }
            }

            $closedAt = Clock::toDb(Clock::nowMs());
            Database::run('UPDATE rounds SET closed_at = ? WHERE id = ?', [$closedAt, $roundId]);

            // Élimination à partir de la manche 3.
            if ((int) $round['number'] >= 3) {
                $standings = self::standings($gameId, (int) $round['number']);
                $toEliminate = Score::eliminationCount(count($standings));
                if ($toEliminate > 0) {
                    // Les moins bien classés d'abord : score croissant, temps cumulé décroissant.
                    usort($standings, fn (array $a, array $b): int =>
                        [$a['score'], -$a['cum_ms']] <=> [$b['score'], -$b['cum_ms']]);
                    foreach (array_slice($standings, 0, $toEliminate) as $loser) {
                        Database::run(
                            'UPDATE game_players SET status = \'eliminated\', eliminated_at = ?
                             WHERE game_id = ? AND player_id = ?',
                            [$closedAt, $gameId, $loser['player_id']]
                        );
                    }
                }
            }
        });
    }

    /** Termine la partie : classement final, vainqueur, historique, score cumulé. */
    public static function finishGame(int $gameId): void
    {
        Database::transaction(function () use ($gameId): void {
            $game = Database::one('SELECT * FROM games WHERE id = ? FOR UPDATE', [$gameId]);
            if ($game === null || !in_array($game['status'], ['running', 'waiting'], true)) {
                return;
            }

            $roundNo = (int) $game['round_no'];
            $players = Database::all(
                'SELECT gp.player_id, gp.score, gp.status FROM game_players gp WHERE gp.game_id = ?',
                [$gameId]
            );
            $standings = self::standings($gameId, $roundNo, false);
            $cumByPlayer = array_column($standings, 'cum_ms', 'player_id');

            // Survivants d'abord, puis score décroissant, puis temps cumulé croissant.
            usort($players, function (array $a, array $b) use ($cumByPlayer): int {
                $aAlive = $a['status'] === 'active' ? 0 : 1;
                $bAlive = $b['status'] === 'active' ? 0 : 1;
                return [$aAlive, -(int) $a['score'], $cumByPlayer[$a['player_id']] ?? PHP_INT_MAX]
                    <=> [$bAlive, -(int) $b['score'], $cumByPlayer[$b['player_id']] ?? PHP_INT_MAX];
            });

            $winnerId = null;
            foreach ($players as $rank => $player) {
                if ($rank === 0 && $player['status'] !== 'left') {
                    $winnerId = (int) $player['player_id'];
                }
                Database::run(
                    'UPDATE game_players SET final_rank = ? WHERE game_id = ? AND player_id = ?',
                    [$rank + 1, $gameId, $player['player_id']]
                );
                Database::run(
                    'UPDATE players SET score_total = score_total + ? WHERE id = ?',
                    [(int) $player['score'], $player['player_id']]
                );
            }

            Database::run(
                'UPDATE games SET status = \'finished\', ended_at = ?, winner_id = ? WHERE id = ?',
                [Clock::toDb(Clock::nowMs()), $winnerId, $gameId]
            );
        });
    }

    /** Annule une partie (hôte parti avant le début, plus aucun joueur...). */
    public static function cancelGame(int $gameId): void
    {
        Database::run(
            'UPDATE games SET status = \'cancelled\', ended_at = ? WHERE id = ? AND status IN (\'waiting\', \'running\')',
            [Clock::toDb(Clock::nowMs()), $gameId]
        );
    }

    /** Dernière manche de la partie (ouverte ou non), ou null. */
    public static function currentRound(int $gameId): ?array
    {
        return Database::one(
            'SELECT * FROM rounds WHERE game_id = ? ORDER BY number DESC LIMIT 1',
            [$gameId]
        );
    }

    public static function activeCount(int $gameId): int
    {
        return (int) Database::scalar(
            'SELECT COUNT(*) FROM game_players WHERE game_id = ? AND status = \'active\'',
            [$gameId]
        );
    }

    /**
     * Classement des joueurs avec temps cumulé de réponse (départage des
     * ex æquo, §2.2). Une manche sans réponse compte pour le temps maximum.
     */
    private static function standings(int $gameId, int $roundsPlayed, bool $activeOnly = true): array
    {
        $filter = $activeOnly ? "AND gp.status = 'active'" : '';
        $rows = Database::all(
            "SELECT gp.player_id, gp.score, gp.status,
                    COALESCE(SUM(a.response_ms), 0) AS answered_ms,
                    COUNT(a.id) AS answered_count
             FROM game_players gp
             LEFT JOIN rounds r ON r.game_id = gp.game_id
             LEFT JOIN answers a ON a.round_id = r.id AND a.player_id = gp.player_id
             WHERE gp.game_id = ? {$filter}
             GROUP BY gp.player_id, gp.score, gp.status",
            [$gameId]
        );
        foreach ($rows as &$row) {
            $missed = max(0, $roundsPlayed - (int) $row['answered_count']);
            $row['score'] = (int) $row['score'];
            $row['player_id'] = (int) $row['player_id'];
            $row['cum_ms'] = (int) $row['answered_ms'] + $missed * Config::questionMs();
        }
        return $rows;
    }

    /** Question aléatoire jamais servie dans cette partie (règle d'équité §2.4). */
    private static function pickQuestion(int $gameId, int $categoryId, string $difficulty): ?int
    {
        $params = [$categoryId, $gameId];
        $difficultyFilter = '';
        if ($difficulty !== 'mixed') {
            $difficultyFilter = 'AND q.difficulty = ?';
            $params[] = $difficulty;
        }
        $id = Database::scalar(
            "SELECT q.id FROM questions q
             WHERE q.active = 1 AND q.category_id = ?
               AND q.id NOT IN (SELECT question_id FROM rounds WHERE game_id = ?)
               {$difficultyFilter}
             ORDER BY RAND() LIMIT 1",
            $params
        );
        return $id === null ? null : (int) $id;
    }
}
