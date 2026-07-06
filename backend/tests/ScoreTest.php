<?php
declare(strict_types=1);

/**
 * Tests unitaires du moteur de score (NF-10, critère A-05).
 * Usage : php backend/tests/ScoreTest.php
 * Sortie non nulle en cas d'échec (utilisable en CI).
 */

require __DIR__ . '/../src/Score.php';

use QuizArena\Score;

$failures = 0;

function check(string $label, mixed $expected, mixed $actual): void
{
    global $failures;
    if ($expected === $actual) {
        echo "  OK   {$label}\n";
    } else {
        $failures++;
        echo "  FAIL {$label} — attendu " . var_export($expected, true)
            . ", obtenu " . var_export($actual, true) . "\n";
    }
}

echo "Formule de score : 700 exactitude + 300 × (temps restant / 15 s)\n";
check('réponse fausse -> 0 point', 0, Score::compute(false, 1000));
check('réponse fausse instantanée -> 0 point', 0, Score::compute(false, 0));
check('réponse correcte instantanée -> 1000 points', 1000, Score::compute(true, 0));
check('réponse correcte à mi-temps (7,5 s) -> 850 points', 850, Score::compute(true, 7500));
check('réponse correcte à la dernière milliseconde -> 700 points', 700, Score::compute(true, 15000));
check('temps de réponse négatif traité comme 0', 1000, Score::compute(true, -50));
check('réponse correcte à 5 s -> 700 + 300×(10/15) = 900', 900, Score::compute(true, 5000));
check('réponse correcte à 14,9 s -> 702 points', 702, Score::compute(true, 14900));

echo "\nÉlimination : dernier quart des actifs, min. 1 à partir de 4 joueurs, jamais tous\n";
check('2 joueurs actifs -> personne', 0, Score::eliminationCount(2));
check('3 joueurs actifs -> personne', 0, Score::eliminationCount(3));
check('4 joueurs actifs -> 1 éliminé', 1, Score::eliminationCount(4));
check('5 joueurs actifs -> 1 éliminé', 1, Score::eliminationCount(5));
check('7 joueurs actifs -> 1 éliminé', 1, Score::eliminationCount(7));
check('8 joueurs actifs -> 2 éliminés', 2, Score::eliminationCount(8));
check('1 joueur actif -> personne', 0, Score::eliminationCount(1));

echo $failures === 0 ? "\nTous les tests sont passés.\n" : "\n{$failures} test(s) en échec.\n";
exit($failures === 0 ? 0 : 1);
