<?php
declare(strict_types=1);

/**
 * Attend que MySQL soit joignable et initialisé (les scripts
 * /docker-entrypoint-initdb.d du conteneur db créent le schéma
 * avant d'ouvrir le port réseau).
 */

$host = getenv('QUIZARENA_DB_HOST') ?: '127.0.0.1';
$port = (int) (getenv('QUIZARENA_DB_PORT') ?: 3306);
$name = getenv('QUIZARENA_DB_NAME') ?: 'quizarena';
$user = getenv('QUIZARENA_DB_USER') ?: 'root';
$password = getenv('QUIZARENA_DB_PASSWORD') !== false ? getenv('QUIZARENA_DB_PASSWORD') : '';

$dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $host, $port, $name);

echo "Attente de MySQL ({$host}:{$port}/{$name})…\n";
for ($attempt = 1; $attempt <= 60; $attempt++) {
    try {
        $pdo = new PDO($dsn, $user, $password, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
        $pdo->query('SELECT 1 FROM categories LIMIT 1');
        echo "Base prête (tentative {$attempt}).\n";
        exit(0);
    } catch (PDOException $e) {
        sleep(1);
    }
}
fwrite(STDERR, "MySQL injoignable après 60 secondes.\n");
exit(1);
