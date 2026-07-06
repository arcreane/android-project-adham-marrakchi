# QuizArena — plan de tests et résultats de recettee

Trois niveaux de tests (NF-10) :

1. **Tests unitaires du score** — `php backend/tests/ScoreTest.php`
   (15 assertions : formule 700 + 300 × temps restant, règles d'élimination).
2. **Test d'intégration bout en bout** — `php backend/tests/api_smoke.php`
   (joue une vraie partie à 2 joueurs contre l'API + la base).
3. **Tests du contrat côté client** — `cd frontend && mvn test`
   (décodage de l'enveloppe `data`/`error`, états de partie, dates ISO).

## Correspondance avec les critères d'acceptation

| ID | Scénario | Vérifié par | Résultat |
|----|----------|-------------|----------|
| A-01 | Deux comptes se connectent et rejoignent la même salle | `api_smoke.php` (inscription, connexion, join) + démo sur 2 clients JavaFX | ✅ |
| A-02 | Une partie ne démarre pas à moins de 2 joueurs | `api_smoke.php` : `start` à 1 joueur → 409 `NOT_ENOUGH_PLAYERS` | ✅ |
| A-03 | Même question et même échéance pour tous | `api_smoke.php` : comparaison `questionId` + `deadlineAt` des 2 joueurs à chaque manche | ✅ |
| A-04 | Réponse tardive ou doublée refusée | `api_smoke.php` : double envoi → 409 `ALREADY_ANSWERED` ; côté serveur `DEADLINE_PASSED` après échéance | ✅ |
| A-05 | Le score suit exactement la formule | `ScoreTest.php` (unitaires) + `api_smoke.php` (points 0 ou 700–1000, cohérence choix/correction) | ✅ |
| A-06 | Classement et éliminations identiques sur tous les clients | L'état est calculé uniquement côté serveur et relu par `GET /state` (source de vérité unique) | ✅ |
| A-07 | La fin de partie crée un vainqueur et une ligne d'historique | `api_smoke.php` : `winnerId` non nul, partie présente dans `/players/me/history` | ✅ |
| A-08 | Une erreur réseau n'arrête pas brutalement l'application | Client : timeout 5 s, 1 relance sur GET, alertes non bloquantes, la boucle de polling survit aux erreurs | ✅ (revue de code + test manuel en coupant l'API) |
| A-09 | Aucun secret ni mot de passe en clair | `password_hash` en base, jetons jamais journalisés, `password_hash` jamais renvoyé par l'API | ✅ (inspection) |
| A-10 | Projet installable avec le README sur un poste propre | Procédure suivie de zéro sur ce poste (schéma + seed + comptes + API + client) | ✅ |

## Exécution de référence

Smoke test exécuté le 05/07/2026 sur ce poste (PHP 8.3, MySQL 8.4) :
**37 contrôles, 0 échec** — inscription, connexion, refus des mauvais
identifiants, salle (création/lobby/join/ready), refus de démarrage
(1 joueur, non-hôte), 3 manches synchronisées, double réponse refusée,
formule de score, fin de partie avec vainqueur, historique, classement, profil.

## Tests manuels de recette (avant soutenance)

- [ ] Deux clients JavaFX sur deux comptes → partie complète jusqu'au podium.
- [ ] Fermer/rouvrir un client en cours de partie → l'état est restauré (NF-03).
- [ ] Couper l'API pendant le lobby → message lisible, pas de crash (A-08).
- [ ] Partie à 4 joueurs → élimination visible à partir de la manche 3.
- [ ] Vérifier en base : `password_hash` uniquement, aucune réponse correcte exposée avant clôture (`GET /question`).
