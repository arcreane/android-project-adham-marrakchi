<?php
declare(strict_types=1);

/**
 * Crée les comptes de démonstration (mot de passe : Demo123!).
 * Le hash bcrypt est généré ici par password_hash — jamais stocké
 * en clair dans le dépôt ni dans les scripts SQL.
 *
 * Usage : php backend/bin/create_demo_accounts.php
 */

spl_autoload_register(static function (string $class): void {
    $prefix = 'QuizArena\\';
    if (str_starts_with($class, $prefix)) {
        $file = __DIR__ . '/../src/' . str_replace('\\', '/', substr($class, strlen($prefix))) . '.php';
        if (is_file($file)) {
            require $file;
        }
    }
});

use QuizArena\Clock;
use QuizArena\Database;

$accounts = [
    ['pseudo' => 'alice', 'email' => 'alice@demo.quizarena.local'],
    ['pseudo' => 'bob',   'email' => 'bob@demo.quizarena.local'],
    ['pseudo' => 'carol', 'email' => 'carol@demo.quizarena.local'],
    ['pseudo' => 'dave',  'email' => 'dave@demo.quizarena.local'],
];
$password = 'Demo123!';

foreach ($accounts as $account) {
    $exists = Database::one('SELECT id FROM players WHERE pseudo = ?', [$account['pseudo']]);
    if ($exists !== null) {
        echo "= {$account['pseudo']} existe déjà (id {$exists['id']})\n";
        continue;
    }
    Database::run(
        'INSERT INTO players (pseudo, email, password_hash, created_at) VALUES (?, ?, ?, ?)',
        [
            $account['pseudo'],
            $account['email'],
            password_hash($password, PASSWORD_DEFAULT),
            Clock::toDb(Clock::nowMs()),
        ]
    );
    echo "+ {$account['pseudo']} créé (mot de passe : {$password})\n";
}
echo "Terminé.\n";
