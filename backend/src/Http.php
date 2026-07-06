<?php
declare(strict_types=1);

namespace QuizArena;

/**
 * Réponses HTTP au format standard du cahier des charges :
 *  - succès : {"data": ...}
 *  - échec  : {"error": {"code": "...", "message": "..."}, "requestId": "..."}
 */
final class Http
{
    public static string $requestId = '';

    public static function json(mixed $data, int $status = 200): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(['data' => $data], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }

    public static function fail(int $status, string $code, string $message): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode([
            'error'     => ['code' => $code, 'message' => $message],
            'requestId' => self::$requestId,
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }

    /** Corps JSON de la requête (objet), 400 si invalide. */
    public static function body(): array
    {
        $raw = file_get_contents('php://input');
        if ($raw === false || trim($raw) === '') {
            return [];
        }
        $decoded = json_decode($raw, true);
        if (!is_array($decoded)) {
            self::fail(400, 'INVALID_JSON', 'Le corps de la requête doit être un objet JSON valide.');
        }
        return $decoded;
    }

    /** Champ obligatoire du corps, 400 si absent. */
    public static function require(array $body, string $field): mixed
    {
        if (!array_key_exists($field, $body) || $body[$field] === null || $body[$field] === '') {
            self::fail(400, 'MISSING_FIELD', "Le champ « {$field} » est obligatoire.");
        }
        return $body[$field];
    }
}
