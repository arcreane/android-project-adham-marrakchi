# API QuizArena — PHP 8.3 + pdo_mysql
# Construite et orchestrée par le docker-compose.yml à la racine du dépôt.
FROM php:8.3-cli-alpine

RUN docker-php-ext-install pdo_mysql

WORKDIR /app
COPY . /app

EXPOSE 8085

# Attend MySQL, crée les comptes de démonstration, puis démarre l'API.
CMD ["sh", "/app/docker/start.sh"]
