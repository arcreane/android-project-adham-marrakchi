<?php
declare(strict_types=1);

namespace QuizArena;

/**
 * Horloge unique du serveur. Toutes les dates sont stockées en UTC
 * au format DATETIME(3) et échangées en ISO-8601 avec millisecondes.
 * L'horodatage serveur fait foi (règle d'équité du cahier des charges).
 */
final class Clock
{
    /** Époque courante en millisecondes. */
    public static function nowMs(): int
    {
        return (int) floor(microtime(true) * 1000);
    }

    /** Millisecondes -> chaîne DATETIME(3) MySQL (UTC). */
    public static function toDb(int $ms): string
    {
        return gmdate('Y-m-d H:i:s', intdiv($ms, 1000))
            . '.' . str_pad((string) ($ms % 1000), 3, '0', STR_PAD_LEFT);
    }

    /** Chaîne DATETIME MySQL (UTC) -> millisecondes. */
    public static function fromDb(?string $value): ?int
    {
        if ($value === null || $value === '') {
            return null;
        }
        if (!preg_match('/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?$/', $value, $m)) {
            return null;
        }
        $seconds = gmmktime((int) $m[4], (int) $m[5], (int) $m[6], (int) $m[2], (int) $m[3], (int) $m[1]);
        $millis = isset($m[7]) ? intdiv((int) str_pad($m[7], 6, '0'), 1000) : 0;
        return $seconds * 1000 + $millis;
    }

    /** Millisecondes -> ISO-8601 UTC avec millisecondes, ex. 2026-07-05T14:03:07.123Z */
    public static function toIso(?int $ms): ?string
    {
        if ($ms === null) {
            return null;
        }
        return gmdate('Y-m-d\TH:i:s', intdiv($ms, 1000))
            . '.' . str_pad((string) ($ms % 1000), 3, '0', STR_PAD_LEFT) . 'Z';
    }

    /** Chaîne DATETIME MySQL -> ISO-8601 (raccourci). */
    public static function dbToIso(?string $value): ?string
    {
        return self::toIso(self::fromDb($value));
    }
}
