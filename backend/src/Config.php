<?php
declare(strict_types=1);

namespace QuizArena;

final class Config
{
    private static ?array $values = null;

    public static function all(): array
    {
        if (self::$values === null) {
            self::$values = require __DIR__ . '/../config/config.php';
        }
        return self::$values;
    }

    /** Accès par chemin pointé, ex. Config::get('db.host'). */
    public static function get(string $path, mixed $default = null): mixed
    {
        $value = self::all();
        foreach (explode('.', $path) as $key) {
            if (!is_array($value) || !array_key_exists($key, $value)) {
                return $default;
            }
            $value = $value[$key];
        }
        return $value;
    }

    public static function questionMs(): int
    {
        return ((int) self::get('game.question_seconds', 15)) * 1000;
    }

    public static function resultsMs(): int
    {
        return ((int) self::get('game.results_seconds', 6)) * 1000;
    }
}
