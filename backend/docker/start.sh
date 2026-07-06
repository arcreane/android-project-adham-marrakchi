#!/bin/sh
# Démarrage du conteneur API : attente de la base, comptes de démo, serveur.
set -e

php /app/docker/wait_for_db.php
php /app/bin/create_demo_accounts.php || echo "(comptes de démonstration déjà présents)"

echo "API QuizArena disponible sur le port 8085 (préfixe /api/v1)"
exec php -S 0.0.0.0:8085 /app/public/index.php
