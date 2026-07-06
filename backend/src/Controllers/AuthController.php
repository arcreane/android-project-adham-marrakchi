<?php
declare(strict_types=1);

namespace QuizArena\Controllers;

use QuizArena\Auth;
use QuizArena\Clock;
use QuizArena\Database;
use QuizArena\Http;

final class AuthController
{
    /** POST /auth/register — F-01 */
    public function register(): never
    {
        $body = Http::body();
        $pseudo = trim((string) Http::require($body, 'pseudo'));
        $email = trim((string) Http::require($body, 'email'));
        $password = (string) Http::require($body, 'password');
        $avatarUrl = isset($body['avatarUrl']) ? trim((string) $body['avatarUrl']) : null;

        if (!preg_match('/^[A-Za-z0-9_-]{3,20}$/', $pseudo)) {
            Http::fail(400, 'INVALID_PSEUDO',
                'Le pseudo doit contenir 3 à 20 caractères (lettres, chiffres, tirets).');
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            Http::fail(400, 'INVALID_EMAIL', 'Adresse email invalide.');
        }
        if (strlen($password) < 8) {
            Http::fail(400, 'WEAK_PASSWORD', 'Le mot de passe doit contenir au moins 8 caractères.');
        }

        // Unicité insensible à la casse (collation utf8mb4_0900_ai_ci + vérification explicite).
        if (Database::one('SELECT id FROM players WHERE pseudo = ?', [$pseudo]) !== null) {
            Http::fail(409, 'PSEUDO_TAKEN', 'Ce pseudo est déjà pris.');
        }
        if (Database::one('SELECT id FROM players WHERE email = ?', [$email]) !== null) {
            Http::fail(409, 'EMAIL_TAKEN', 'Un compte existe déjà avec cet email.');
        }

        Database::run(
            'INSERT INTO players (pseudo, email, password_hash, avatar_url, created_at)
             VALUES (?, ?, ?, ?, ?)',
            [$pseudo, $email, password_hash($password, PASSWORD_DEFAULT), $avatarUrl ?: null,
             Clock::toDb(Clock::nowMs())]
        );
        $playerId = (int) Database::pdo()->lastInsertId();

        Http::json([
            'token'  => Auth::issueToken($playerId),
            'player' => PlayerController::profile($playerId),
        ], 201);
    }

    /** POST /auth/login — F-02 (connexion par pseudo ou email) */
    public function login(): never
    {
        $body = Http::body();
        $login = trim((string) Http::require($body, 'login'));
        $password = (string) Http::require($body, 'password');

        $player = Database::one(
            'SELECT id, password_hash, status FROM players WHERE pseudo = ? OR email = ?',
            [$login, $login]
        );
        if ($player === null || !password_verify($password, $player['password_hash'])) {
            Http::fail(401, 'BAD_CREDENTIALS', 'Identifiants incorrects.');
        }
        if ($player['status'] === 'banned') {
            Http::fail(403, 'BANNED', 'Ce compte a été banni.');
        }

        Http::json([
            'token'  => Auth::issueToken((int) $player['id']),
            'player' => PlayerController::profile((int) $player['id']),
        ]);
    }
}
