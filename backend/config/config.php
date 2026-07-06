<?php
declare(strict_types=1);

/**
 * Configuration du backend QuizArena.
 * Pour surcharger localement sans toucher au dépôt, créer config/config.local.php
 * qui retourne un tableau avec les mêmes clés (fusionné par-dessus celui-ci).
 */

$config = [
    'db' => [
        'host'     => getenv('QUIZARENA_DB_HOST') ?: '127.0.0.1',
        'port'     => (int) (getenv('QUIZARENA_DB_PORT') ?: 3306),
        'name'     => getenv('QUIZARENA_DB_NAME') ?: 'quizarena',
        'user'     => getenv('QUIZARENA_DB_USER') ?: 'root',
        'password' => getenv('QUIZARENA_DB_PASSWORD') !== false ? getenv('QUIZARENA_DB_PASSWORD') : '',
    ],
    'game' => [
        'question_seconds' => 15,   // temps de réponse par question
        'results_seconds'  => 6,    // durée d'affichage du résultat de manche
        'rounds_default'   => 10,   // nombre de manches par défaut (maximum du cahier des charges)
        'token_ttl_hours'  => 24,   // durée de vie d'un jeton de session
    ],
    'log_file' => __DIR__ . '/../logs/app.log',
];

$local = __DIR__ . '/config.local.php';
if (is_file($local)) {
    $config = array_replace_recursive($config, require $local);
}

return $config;
