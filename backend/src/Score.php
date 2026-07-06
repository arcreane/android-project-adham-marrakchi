<?php
declare(strict_types=1);

namespace QuizArena;

/**
 * Règles de score et d'élimination — fonctions pures, testées unitairement.
 *
 * Score d'une manche (cahier des charges §2.2) :
 *   0 point si la réponse est fausse ou absente ;
 *   sinon 700 points d'exactitude + jusqu'à 300 points de rapidité,
 *   proportionnels au temps restant (1000 points pour une réponse instantanée).
 */
final class Score
{
    public const CORRECT_POINTS = 700;
    public const SPEED_POINTS = 300;

    public static function compute(bool $correct, int $responseMs, int $timeLimitMs = 15000): int
    {
        if (!$correct || $timeLimitMs <= 0) {
            return 0;
        }
        $remaining = max(0, $timeLimitMs - max(0, $responseMs));
        return self::CORRECT_POINTS + (int) round(self::SPEED_POINTS * $remaining / $timeLimitMs);
    }

    /**
     * Nombre de joueurs à éliminer à la fin d'une manche (à partir de la manche 3) :
     * le dernier quart des joueurs actifs, au minimum un joueur quand
     * 4 joueurs ou plus restent en lice. Jamais tous les joueurs.
     */
    public static function eliminationCount(int $activePlayers): int
    {
        if ($activePlayers < 4) {
            return 0;
        }
        return min(max(1, intdiv($activePlayers, 4)), $activePlayers - 1);
    }
}
