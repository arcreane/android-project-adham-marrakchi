<?php
declare(strict_types=1);

namespace QuizArena\Controllers;

use QuizArena\Database;
use QuizArena\Http;

final class LeaderboardController
{
    /** GET /leaderboard — F-12 : meilleurs joueurs par score cumulé. */
    public function top(): never
    {
        $rows = Database::all(
            'SELECT p.id, p.pseudo, p.avatar_url, p.score_total,
                    (SELECT COUNT(*) FROM games w WHERE w.winner_id = p.id AND w.status = \'finished\') AS wins
             FROM players p
             WHERE p.status = \'active\'
             ORDER BY p.score_total DESC, p.pseudo ASC
             LIMIT 20'
        );
        $entries = [];
        foreach ($rows as $i => $r) {
            $entries[] = [
                'rank'       => $i + 1,
                'playerId'   => (int) $r['id'],
                'pseudo'     => $r['pseudo'],
                'avatarUrl'  => $r['avatar_url'],
                'scoreTotal' => (int) $r['score_total'],
                'wins'       => (int) $r['wins'],
            ];
        }
        Http::json($entries);
    }

    /** GET /categories : catalogue pour la création de salle. */
    public function categories(): never
    {
        $rows = Database::all(
            'SELECT id, name, icon_url, color_hex FROM categories WHERE active = 1 ORDER BY name'
        );
        Http::json(array_map(static fn (array $r): array => [
            'id'       => (int) $r['id'],
            'name'     => $r['name'],
            'iconUrl'  => $r['icon_url'],
            'colorHex' => $r['color_hex'],
        ], $rows));
    }
}
