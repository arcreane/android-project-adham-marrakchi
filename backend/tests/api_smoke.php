<?php
declare(strict_types=1);

/**
 * Test d'intégration bout en bout (NF-10, critères A-01 à A-07).
 * Joue une partie complète à deux joueurs contre une API démarrée :
 * inscription, connexion, création/rejoindre une salle, lancement,
 * questions/réponses jusqu'au podium, puis historique et classement.
 *
 * Usage : php backend/tests/api_smoke.php [url_de_base] [manches]
 *   défauts : http://127.0.0.1:8080/api/v1  et  3 manches
 */

$baseUrl = $argv[1] ?? 'http://127.0.0.1:8080/api/v1';
$rounds = (int) ($argv[2] ?? 3);
$failures = 0;

function request(string $method, string $url, ?array $body = null, ?string $token = null): array
{
    $headers = ['Content-Type: application/json', 'Accept: application/json'];
    if ($token !== null) {
        $headers[] = 'Authorization: Bearer ' . $token;
    }
    $context = stream_context_create(['http' => [
        'method'        => $method,
        'header'        => implode("\r\n", $headers),
        'content'       => $body !== null ? json_encode($body) : '',
        'ignore_errors' => true,
        'timeout'       => 10,
    ]]);
    $raw = file_get_contents($url, false, $context);
    $status = 0;
    foreach ($http_response_header ?? [] as $line) {
        if (preg_match('#^HTTP/\S+\s+(\d{3})#', $line, $m)) {
            $status = (int) $m[1];
        }
    }
    return ['status' => $status, 'json' => $raw !== false ? json_decode($raw, true) : null];
}

function check(string $label, bool $condition, mixed $detail = null): void
{
    global $failures;
    if ($condition) {
        echo "  OK   {$label}\n";
    } else {
        $failures++;
        echo "  FAIL {$label}" . ($detail !== null ? ' — ' . json_encode($detail, JSON_UNESCAPED_UNICODE) : '') . "\n";
    }
}

echo "API : {$baseUrl} ({$rounds} manches)\n\n";
$suffix = substr(bin2hex(random_bytes(4)), 0, 6);

// --- A-01 : deux comptes se connectent -----------------------------------
$players = [];
foreach (['smokeA', 'smokeB'] as $name) {
    $pseudo = $name . $suffix;
    $r = request('POST', "$baseUrl/auth/register", [
        'pseudo' => $pseudo, 'email' => "$pseudo@test.local", 'password' => 'Test1234!',
    ]);
    check("inscription {$pseudo} (201)", $r['status'] === 201, $r);
    $r = request('POST', "$baseUrl/auth/login", ['login' => $pseudo, 'password' => 'Test1234!']);
    check("connexion {$pseudo} (200 + token)", $r['status'] === 200 && !empty($r['json']['data']['token']), $r);
    $players[$name] = [
        'token' => $r['json']['data']['token'],
        'id'    => $r['json']['data']['player']['id'],
    ];
}
$a = $players['smokeA'];
$b = $players['smokeB'];

// Mauvais mot de passe -> 401 ; route privée sans jeton -> 401.
$r = request('POST', "$baseUrl/auth/login", ['login' => 'smokeA' . $suffix, 'password' => 'mauvais']);
check('mauvais mot de passe refusé (401)', $r['status'] === 401);
$r = request('GET', "$baseUrl/games");
check('route privée sans jeton refusée (401)', $r['status'] === 401);

// --- Salle : création, lobby, rejoindre ----------------------------------
$r = request('GET', "$baseUrl/categories", null, $a['token']);
check('catégories disponibles', $r['status'] === 200 && count($r['json']['data']) >= 4, $r);
$categoryId = $r['json']['data'][0]['id'];

$r = request('POST', "$baseUrl/games",
    ['categoryId' => $categoryId, 'difficulty' => 'mixed', 'maxPlayers' => 4, 'rounds' => $rounds],
    $a['token']);
check('création de salle (201)', $r['status'] === 201, $r);
$gameId = $r['json']['data']['id'];

// A-02 : pas de démarrage à moins de 2 joueurs.
$r = request('POST', "$baseUrl/games/$gameId/start", [], $a['token']);
check('démarrage refusé à 1 joueur (409)', $r['status'] === 409, $r);

$r = request('GET', "$baseUrl/games", null, $b['token']);
$visible = array_filter($r['json']['data'] ?? [], fn ($g) => $g['id'] === $gameId);
check('salle visible dans le lobby', count($visible) === 1);

$r = request('POST', "$baseUrl/games/$gameId/join", [], $b['token']);
check('B rejoint la salle (200)', $r['status'] === 200, $r);
$r = request('POST', "$baseUrl/games/$gameId/ready", ['ready' => true], $b['token']);
check('B se déclare prêt (200)', $r['status'] === 200, $r);

$r = request('POST', "$baseUrl/games/$gameId/start", [], $b['token']);
check('démarrage refusé pour un non-hôte (403)', $r['status'] === 403, $r);
$r = request('POST', "$baseUrl/games/$gameId/start", [], $a['token']);
check('démarrage par l\'hôte (200)', $r['status'] === 200, $r);

// --- Boucle de jeu ---------------------------------------------------------
$roundsSeen = 0;
$scoreChecked = false;
for ($guard = 0; $guard < 200; $guard++) {
    $r = request('GET', "$baseUrl/games/$gameId/state", null, $a['token']);
    $state = $r['json']['data'] ?? null;
    if ($state === null) {
        check('état de partie lisible', false, $r);
        break;
    }
    if ($state['phase'] === 'finished') {
        break;
    }
    if ($state['phase'] === 'question') {
        $qa = request('GET', "$baseUrl/games/$gameId/question", null, $a['token']);
        $qb = request('GET', "$baseUrl/games/$gameId/question", null, $b['token']);
        $qaData = $qa['json']['data'] ?? null;
        $qbData = $qb['json']['data'] ?? null;
        if ($qaData !== null && $qbData !== null && !$qaData['answered']) {
            $roundsSeen++;
            // A-03 : même question et même échéance pour tous.
            check("manche {$qaData['roundNumber']} : question identique pour A et B",
                $qaData['questionId'] === $qbData['questionId']
                && $qaData['deadlineAt'] === $qbData['deadlineAt']);
            check('la solution n\'est pas exposée avant clôture (NF-05)',
                !isset($qaData['correctIndex']) && !isset($qbData['correctIndex']));

            $choiceA = random_int(0, 3);
            $ra = request('POST', "$baseUrl/games/$gameId/answers",
                ['questionId' => $qaData['questionId'], 'choiceIndex' => $choiceA], $a['token']);
            check('réponse de A acceptée (201)', $ra['status'] === 201, $ra);

            // A-04 : double soumission refusée.
            $dup = request('POST', "$baseUrl/games/$gameId/answers",
                ['questionId' => $qaData['questionId'], 'choiceIndex' => $choiceA], $a['token']);
            check('double réponse refusée (409)', $dup['status'] === 409, $dup);

            $rb = request('POST', "$baseUrl/games/$gameId/answers",
                ['questionId' => $qbData['questionId'], 'choiceIndex' => random_int(0, 3)], $b['token']);
            check('réponse de B acceptée (201)', $rb['status'] === 201, $rb);

            // A-05 : le score suit la formule (0 si faux, 700..1000 si juste).
            $sc = request('GET', "$baseUrl/games/$gameId/scores", null, $a['token']);
            $row = null;
            foreach ($sc['json']['data']['ranking'] ?? [] as $entry) {
                if ($entry['playerId'] === $a['id']) {
                    $row = $entry;
                }
            }
            if ($row !== null && $row['lastCorrect'] !== null && !$scoreChecked) {
                $scoreChecked = true;
                $expectedRange = $row['lastCorrect']
                    ? ($row['lastPoints'] >= 700 && $row['lastPoints'] <= 1000)
                    : $row['lastPoints'] === 0;
                check('points conformes à la formule (0 ou 700–1000)', $expectedRange, $row);
                check('cohérence correction/choix',
                    $row['lastCorrect'] === ($row['lastChoice'] === $sc['json']['data']['correctIndex']), $row);
            }
        }
    }
    usleep(400_000);
}

check("au moins {$rounds} manches jouées", $roundsSeen >= $rounds, $roundsSeen);

// --- A-07 : fin de partie, vainqueur, historique ---------------------------
$r = request('GET', "$baseUrl/games/$gameId/state", null, $b['token']);
$state = $r['json']['data'] ?? [];
check('partie terminée avec un vainqueur', ($state['phase'] ?? '') === 'finished' && !empty($state['winnerId']), $state);
check('classement final attribué à tous',
    !array_filter($state['players'] ?? [['finalRank' => null]], fn ($p) => $p['finalRank'] === null));

$r = request('GET', "$baseUrl/players/me/history", null, $a['token']);
$inHistory = array_filter($r['json']['data'] ?? [], fn ($h) => $h['gameId'] === $gameId);
check('la partie apparaît dans l\'historique', count($inHistory) === 1, $r['json']['data'] ?? null);

$r = request('GET', "$baseUrl/leaderboard", null, $a['token']);
check('classement global disponible', $r['status'] === 200 && is_array($r['json']['data']));

$r = request('GET', "$baseUrl/players/me", null, $a['token']);
check('profil avec statistiques', $r['status'] === 200 && ($r['json']['data']['player']['gamesPlayed'] ?? 0) >= 1, $r);

echo $failures === 0
    ? "\nSMOKE TEST : tous les contrôles sont passés.\n"
    : "\nSMOKE TEST : {$failures} contrôle(s) en échec.\n";
exit($failures === 0 ? 0 : 1);
