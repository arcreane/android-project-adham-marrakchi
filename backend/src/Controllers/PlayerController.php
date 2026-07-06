<?php
declare(strict_types=1);

namespace QuizArena\Controllers;

use QuizArena\Clock;
use QuizArena\Database;
use QuizArena\Http;

final class PlayerController
{
    /** GET /players/me — F-11 */
    public function me(array $player): never
    {
        Http::json([
            'player'    => self::profile($player['id']),
            'lastGames' => self::historyRows($player['id'], 5),
        ]);
    }

    /** GET /players/me/history */
    public function history(array $player): never
    {
        Http::json(self::historyRows($player['id'], 50));
    }

    /** Profil public d'un joueur (jamais de password_hash — NF-05). */
    public static function profile(int $playerId): array
    {
        $p = Database::one(
            'SELECT id, pseudo, avatar_url, score_total, created_at FROM players WHERE id = ?',
            [$playerId]
        );
        if ($p === null) {
            Http::fail(404, 'PLAYER_NOT_FOUND', 'Joueur introuvable.');
        }
        $gamesPlayed = (int) Database::scalar(
            'SELECT COUNT(*) FROM game_players gp
             JOIN games g ON g.id = gp.game_id
             WHERE gp.player_id = ? AND g.status = \'finished\'',
            [$playerId]
        );
        $wins = (int) Database::scalar(
            'SELECT COUNT(*) FROM games WHERE winner_id = ? AND status = \'finished\'',
            [$playerId]
        );
        return [
            'id'          => (int) $p['id'],
            'pseudo'      => $p['pseudo'],
            'avatarUrl'   => $p['avatar_url'],
            'scoreTotal'  => (int) $p['score_total'],
            'gamesPlayed' => $gamesPlayed,
            'wins'        => $wins,
            'createdAt'   => Clock::dbToIso($p['created_at']),
        ];
    }

    private static function historyRows(int $playerId, int $limit): array
    {
        $rows = Database::all(
            'SELECT g.id, g.ended_at, g.started_at, c.name AS category, gp.final_rank, gp.score,
                    (SELECT COUNT(*) FROM game_players x WHERE x.game_id = g.id) AS players_count
             FROM game_players gp
             JOIN games g ON g.id = gp.game_id
             JOIN categories c ON c.id = g.category_id
             WHERE gp.player_id = ? AND g.status = \'finished\'
             ORDER BY g.ended_at DESC
             LIMIT ' . $limit,
            [$playerId]
        );
        return array_map(static function (array $r): array {
            $started = Clock::fromDb($r['started_at']);
            $ended = Clock::fromDb($r['ended_at']);
            return [
                'gameId'          => (int) $r['id'],
                'endedAt'         => Clock::toIso($ended),
                'categoryName'    => $r['category'],
                'finalRank'       => $r['final_rank'] !== null ? (int) $r['final_rank'] : null,
                'score'           => (int) $r['score'],
                'playersCount'    => (int) $r['players_count'],
                'durationSeconds' => ($started !== null && $ended !== null)
                    ? intdiv($ended - $started, 1000) : null,
            ];
        }, $rows);
    }
}
