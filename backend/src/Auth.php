<?php
declare(strict_types=1);

namespace QuizArena;

/**
 * Authentification par jeton opaque (Authorization: Bearer <token>).
 * Les mots de passe sont hachés avec password_hash / password_verify (NF-04),
 * jamais stockés ni journalisés en clair.
 */
final class Auth
{
    /** Crée un jeton de session pour un joueur et le retourne. */
    public static function issueToken(int $playerId): string
    {
        $token = bin2hex(random_bytes(32));
        $now = Clock::nowMs();
        $ttlHours = (int) Config::get('game.token_ttl_hours', 24);
        Database::run(
            'INSERT INTO tokens (player_id, token, created_at, expires_at) VALUES (?, ?, ?, ?)',
            [$playerId, $token, Clock::toDb($now), Clock::toDb($now + $ttlHours * 3600 * 1000)]
        );
        return $token;
    }

    /**
     * Vérifie l'en-tête Authorization et retourne la ligne joueur.
     * Répond 401/403 directement en cas d'échec.
     */
    public static function requirePlayer(): array
    {
        $header = self::authorizationHeader();
        if ($header === null || !preg_match('/^Bearer\s+([A-Fa-f0-9]{64})$/', trim($header), $m)) {
            Http::fail(401, 'UNAUTHENTICATED', 'Jeton d\'authentification manquant ou mal formé.');
        }
        $token = strtolower($m[1]);

        $row = Database::one(
            'SELECT t.expires_at, p.id, p.pseudo, p.email, p.avatar_url, p.score_total, p.status
             FROM tokens t
             JOIN players p ON p.id = t.player_id
             WHERE t.token = ?',
            [$token]
        );
        if ($row === null) {
            Http::fail(401, 'INVALID_TOKEN', 'Session invalide, veuillez vous reconnecter.');
        }
        if ((Clock::fromDb($row['expires_at']) ?? 0) < Clock::nowMs()) {
            Database::run('DELETE FROM tokens WHERE token = ?', [$token]);
            Http::fail(401, 'SESSION_EXPIRED', 'Session expirée, veuillez vous reconnecter.');
        }
        if ($row['status'] === 'banned') {
            Http::fail(403, 'BANNED', 'Ce compte a été banni.');
        }
        unset($row['expires_at']);
        $row['id'] = (int) $row['id'];
        return $row;
    }

    private static function authorizationHeader(): ?string
    {
        if (function_exists('getallheaders')) {
            foreach (getallheaders() as $name => $value) {
                if (strcasecmp($name, 'Authorization') === 0) {
                    return $value;
                }
            }
        }
        return $_SERVER['HTTP_AUTHORIZATION'] ?? null;
    }
}
