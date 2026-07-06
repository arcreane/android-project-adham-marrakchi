<?php
declare(strict_types=1);

/**
 * QuizArena — API REST (POC PHP)
 * Point d'entrée unique. Lancement en développement :
 *   php -S 127.0.0.1:8080 backend/public/index.php
 *
 * Contrat : préfixe /api/v1, JSON UTF-8, Authorization: Bearer <token>
 * sur toutes les routes privées. Voir docs/openapi.yaml.
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

use QuizArena\Auth;
use QuizArena\Controllers\AuthController;
use QuizArena\Controllers\GameController;
use QuizArena\Controllers\LeaderboardController;
use QuizArena\Controllers\PlayerController;
use QuizArena\Http;
use QuizArena\Log;

Http::$requestId = bin2hex(random_bytes(6));
header('X-Request-Id: ' . Http::$requestId);

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';

try {
    dispatch($method, $path);
} catch (\PDOException $e) {
    Log::error('Erreur base de données : ' . $e->getMessage(), ['method' => $method, 'path' => $path]);
    Http::fail(500, 'DATABASE_ERROR', 'Erreur interne du serveur (base de données).');
} catch (\Throwable $e) {
    Log::error($e::class . ' : ' . $e->getMessage(), [
        'method' => $method,
        'path'   => $path,
        'at'     => $e->getFile() . ':' . $e->getLine(),
    ]);
    Http::fail(500, 'SERVER_ERROR', 'Erreur interne du serveur.');
}

function dispatch(string $method, string $path): never
{
    $prefix = '/api/v1';
    if (!str_starts_with($path, $prefix)) {
        Http::fail(404, 'NOT_FOUND', 'Route inconnue. Toutes les routes commencent par /api/v1.');
    }
    $route = rtrim(substr($path, strlen($prefix)), '/') ?: '/';

    // --- Routes publiques -------------------------------------------------
    if ($method === 'POST' && $route === '/auth/register') {
        (new AuthController())->register();
    }
    if ($method === 'POST' && $route === '/auth/login') {
        (new AuthController())->login();
    }

    // --- Routes privées ---------------------------------------------------
    $player = Auth::requirePlayer();

    if ($method === 'GET' && $route === '/players/me') {
        (new PlayerController())->me($player);
    }
    if ($method === 'GET' && $route === '/players/me/history') {
        (new PlayerController())->history($player);
    }
    if ($method === 'GET' && $route === '/leaderboard') {
        (new LeaderboardController())->top();
    }
    if ($method === 'GET' && $route === '/categories') {
        (new LeaderboardController())->categories();
    }
    if ($method === 'GET' && $route === '/games') {
        (new GameController())->index();
    }
    if ($method === 'POST' && $route === '/games') {
        (new GameController())->create($player);
    }

    if (preg_match('#^/games/(\d+)(/[a-z]+)?$#', $route, $m) === 1) {
        $gameId = (int) $m[1];
        $action = $m[2] ?? '';
        $controller = new GameController();
        match (true) {
            $method === 'POST' && $action === '/join'     => $controller->join($player, $gameId),
            $method === 'POST' && $action === '/ready'    => $controller->ready($player, $gameId),
            $method === 'POST' && $action === '/start'    => $controller->start($player, $gameId),
            $method === 'POST' && $action === '/leave'    => $controller->leave($player, $gameId),
            $method === 'POST' && $action === '/answers'  => $controller->answer($player, $gameId),
            $method === 'GET'  && $action === '/state'    => $controller->state($player, $gameId),
            $method === 'GET'  && $action === '/question' => $controller->question($player, $gameId),
            $method === 'GET'  && $action === '/scores'   => $controller->scores($player, $gameId),
            default => Http::fail(404, 'NOT_FOUND', 'Route inconnue.'),
        };
    }

    Http::fail(404, 'NOT_FOUND', 'Route inconnue.');
}
