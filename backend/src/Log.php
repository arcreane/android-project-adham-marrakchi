<?php
declare(strict_types=1);

namespace QuizArena;

/**
 * Journal d'erreurs horodaté avec identifiant de requête (NF-08).
 * Ne jamais y écrire de jeton, de mot de passe ni de hash.
 */
final class Log
{
    public static function error(string $message, array $context = []): void
    {
        self::write('ERROR', $message, $context);
    }

    public static function info(string $message, array $context = []): void
    {
        self::write('INFO', $message, $context);
    }

    private static function write(string $level, string $message, array $context): void
    {
        $file = (string) Config::get('log_file');
        $dir = dirname($file);
        if (!is_dir($dir)) {
            @mkdir($dir, 0777, true);
        }
        $line = sprintf(
            "[%s] %s req=%s %s%s\n",
            Clock::toIso(Clock::nowMs()),
            $level,
            Http::$requestId !== '' ? Http::$requestId : '-',
            $message,
            $context !== [] ? ' ' . json_encode($context, JSON_UNESCAPED_UNICODE) : ''
        );
        @file_put_contents($file, $line, FILE_APPEND | LOCK_EX);
    }
}
