[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/6XGyrBDV)

# QuizArena — Battle Royale de culture générale

- BENALLAL Ghizlane (@gh832) — Frontend
- MARRAKCHI Adham (@AdaMakchi) — Backend

Projet multijoueur (2 à 8 joueurs) réalisé d'après le [cahier des charges](docs/Cahier_des_charges_QuizArena.docx) :
un quiz à élimination progressive, en manches synchronisées de 15 secondes,
avec un frontend **JavaFX**, une API REST **PHP 8** et une base **MySQL 8 / MariaDB**.

```
┌────────────────┐   HTTP JSON (polling 1-2 s)   ┌──────────────┐   PDO   ┌───────────┐
│ Client JavaFX  │ ────────────────────────────► │ API PHP 8    │ ──────► │ MySQL 8   │
│ (frontend/)    │   Authorization: Bearer …     │ (backend/)   │         │ quizarena │
└────────────────┘                               └──────────────┘         └───────────┘
```

Le frontend ne se connecte **jamais** directement à la base : toute donnée passe
par l'API `/api/v1` (source de vérité unique, scores calculés côté serveur).

## Prérequis

| Composant | Version | Remarque |
|-----------|---------|----------|
| Java (JDK) | 17 ou + | testé avec JDK 23 — requis dans tous les cas (client de bureau) |
| Maven | 3.8 ou + | ou utiliser un IDE (IntelliJ) qui l'embarque |
| **Option A — Docker** | Docker Desktop | remplace PHP et MySQL, recommandé |
| **Option B — manuel** | PHP 8.1+ (`pdo_mysql`) et MySQL 8.x | ex. Laragon/XAMPP |

## Option A — Docker (recommandé : 2 commandes)

Le backend complet (MySQL + API, schéma, 60 questions et comptes de
démonstration inclus) démarre en une commande :

```bash
docker compose up -d --build      # API sur http://127.0.0.1:8085/api/v1
cd frontend && mvn javafx:run     # client JavaFX (natif : application de bureau)
```

Vérification automatisée (joue une vraie partie à 2 joueurs dans un conteneur) :

```bash
docker compose --profile test run --rm smoke
```

Commandes utiles : `docker compose logs -f api` (journaux),
`docker compose down` (arrêt), `docker compose down -v` (arrêt + base remise à zéro).
La base est aussi consultable depuis l'hôte sur le port **3307**
(root / `quizarena-root`).

> Seul le backend est conteneurisé : le client JavaFX est une application
> de bureau (fenêtres, clavier) et reste lancé nativement. Pour jouer depuis
> un autre poste du réseau, pointez son `config.properties` sur
> `http://IP_DU_SERVEUR:8085/api/v1`.

## Option B — Installation manuelle (poste propre)

### 1. Base de données

```bash
mysql -u root < backend/sql/schema.sql     # crée la base quizarena + tables
mysql -u root quizarena < backend/sql/seed.sql   # 4 catégories, 60 questions
php backend/bin/create_demo_accounts.php   # comptes de démonstration
```

Identifiants MySQL différents ? Surchargez par variables d'environnement
(`QUIZARENA_DB_USER`, `QUIZARENA_DB_PASSWORD`, `QUIZARENA_DB_HOST`, `QUIZARENA_DB_PORT`)
ou créez `backend/config/config.local.php` (voir `backend/config/config.php`).

### 2. API PHP

```bash
php -S 127.0.0.1:8085 backend/public/index.php
```

> Port 8085 par défaut : le port 8080 est souvent occupé (Apache/Tomcat).
> Si vous changez le port, mettez à jour `api.baseUrl` côté client (étape 3).

Vérification rapide : `php backend/tests/api_smoke.php` joue une partie
complète à 2 joueurs et contrôle les règles (voir « Tests »).

### 3. Client JavaFX

```bash
cd frontend
mvn javafx:run
```

L'URL de l'API est externalisée : copier `frontend/src/main/resources/config.properties`
à côté du lancement (répertoire courant) pour la surcharger sans recompiler :

```properties
api.baseUrl=http://127.0.0.1:8085/api/v1
```

Pour jouer à plusieurs sur un même poste, lancer un second client
(`mvn javafx:run` dans un autre terminal) et se connecter avec un autre compte.

## Comptes de démonstration

| Pseudo | Mot de passe |
|--------|--------------|
| alice  | `Demo123!` |
| bob    | `Demo123!` |
| carol  | `Demo123!` |
| dave   | `Demo123!` |

## Règles du jeu

- QCM à 4 choix, **15 secondes** par question, **10 manches** maximum (paramétrable 3-10).
- Score par manche : **700 points** d'exactitude + jusqu'à **300 points** de rapidité.
- À partir de la manche 3 : élimination du dernier quart des joueurs actifs
  (au minimum un joueur quand ils sont 4 ou plus).
- Vainqueur : dernier joueur en lice, ou meilleur score à la fin des manches.
- Équité : l'horodatage **serveur** fait foi, une réponse en retard ou doublée est
  refusée (HTTP 409), la bonne réponse n'est jamais transmise avant la clôture.

## Tests

```bash
php backend/tests/ScoreTest.php        # tests unitaires du score et de l'élimination
php backend/tests/api_smoke.php        # partie complète bout en bout (API démarrée)
cd frontend && mvn test                # tests du décodage du contrat JSON côté client
```

Le plan de tests détaillé (critères A-01 à A-10) : [docs/plan-de-tests.md](docs/plan-de-tests.md).

## Structure du dépôt

```
backend/
  public/index.php      point d'entrée + routeur /api/v1
  src/                  Auth, Engine (moteur de partie), Score, contrôleurs
  sql/schema.sql        schéma complet (7 tables + jetons)
  sql/seed.sql          4 catégories, 60 questions
  bin/                  création des comptes de démonstration
  tests/                tests unitaires + smoke test bout en bout
frontend/
  pom.xml               Maven, JavaFX 21, Gson
  src/main/java/        écrans (8), client API, polling, navigation
  src/main/resources/   FXML, CSS, config.properties
docs/
  Cahier_des_charges_QuizArena.docx
  openapi.yaml          contrat d'API complet
  plan-de-tests.md      critères d'acceptation A-01 à A-10
```

## Dépannage

| Symptôme | Cause probable | Solution |
|----------|----------------|----------|
| `Serveur injoignable` dans le client | API non démarrée ou mauvais port | relancer `php -S 127.0.0.1:8085 …`, vérifier `api.baseUrl` |
| `SQLSTATE[HY000] [2002]` côté PHP | MySQL éteint | démarrer MySQL (Laragon : bouton « Start ») |
| `Failed to listen on 127.0.0.1:8085` | port déjà pris | choisir un autre port des deux côtés |
| 404 sur toutes les routes | URL sans `/api/v1` | vérifier le préfixe |
| La partie n'avance plus | plus aucun client ne poll | rouvrir un client : l'état est restauré depuis l'API |
| `Session expirée` | jeton > 24 h | se reconnecter (le client le propose) |

## Évolution prévue (hors POC)

Le backend PHP implémente le contrat décrit dans `docs/openapi.yaml`. La
réécriture **Spring Boot** devra exposer exactement le même contrat (routes,
enveloppe `data`/`error`, codes HTTP) : le client JavaFX ne change pas.
