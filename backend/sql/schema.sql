-- ============================================================
-- QuizArena — schéma MySQL 8 / MariaDB (cahier des charges §6)
-- Usage : mysql -u root < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS quizarena
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE quizarena;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS answers, rounds, game_players, games, questions, categories, tokens, players;
SET FOREIGN_KEY_CHECKS = 1;

-- Comptes joueurs. Unicité pseudo/email insensible à la casse
-- (collation utf8mb4_0900_ai_ci). Jamais de mot de passe en clair (NF-04).
CREATE TABLE players (
    id            INT UNSIGNED     NOT NULL AUTO_INCREMENT,
    pseudo        VARCHAR(20)      NOT NULL,
    email         VARCHAR(190)     NOT NULL,
    password_hash VARCHAR(255)     NOT NULL,
    avatar_url    VARCHAR(500)     NULL,
    score_total   INT              NOT NULL DEFAULT 0,
    status        ENUM('active','banned') NOT NULL DEFAULT 'active',
    created_at    DATETIME(3)      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_players_pseudo (pseudo),
    UNIQUE KEY uq_players_email (email),
    KEY idx_players_score (score_total DESC)
) ENGINE = InnoDB;

-- Jetons de session (Authorization: Bearer).
CREATE TABLE tokens (
    id         INT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_id  INT UNSIGNED NOT NULL,
    token      CHAR(64)     NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    expires_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tokens_token (token),
    CONSTRAINT fk_tokens_player FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Catalogue de catégories.
CREATE TABLE categories (
    id        INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name      VARCHAR(80)  NOT NULL,
    icon_url  VARCHAR(500) NULL,
    color_hex CHAR(7)      NULL,
    active    TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_categories_name (name)
) ENGINE = InnoDB;

-- Questions QCM à 4 choix, une seule bonne réponse.
-- Suppression logique via active = 0 quand la question a déjà servi (§6.1).
CREATE TABLE questions (
    id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id   INT UNSIGNED NOT NULL,
    text          TEXT         NOT NULL,
    choices_json  JSON         NOT NULL,
    correct_index TINYINT      NOT NULL,
    difficulty    ENUM('easy','medium','hard') NOT NULL DEFAULT 'medium',
    image_url     VARCHAR(500) NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_questions_pick (category_id, difficulty, active),
    CONSTRAINT fk_questions_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_questions_correct CHECK (correct_index BETWEEN 0 AND 3)
) ENGINE = InnoDB;

-- Salles / parties.
CREATE TABLE games (
    id           INT UNSIGNED NOT NULL AUTO_INCREMENT,
    host_id      INT UNSIGNED NOT NULL,
    category_id  INT UNSIGNED NOT NULL,
    difficulty   ENUM('easy','medium','hard','mixed') NOT NULL DEFAULT 'mixed',
    status       ENUM('waiting','running','finished','cancelled') NOT NULL DEFAULT 'waiting',
    max_players  TINYINT UNSIGNED NOT NULL DEFAULT 8,
    rounds_total TINYINT UNSIGNED NOT NULL DEFAULT 10,
    round_no     TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at   DATETIME(3)  NOT NULL,
    started_at   DATETIME(3)  NULL,
    ended_at     DATETIME(3)  NULL,
    winner_id    INT UNSIGNED NULL,
    PRIMARY KEY (id),
    KEY idx_games_lobby (status, created_at),
    CONSTRAINT fk_games_host FOREIGN KEY (host_id) REFERENCES players (id),
    CONSTRAINT fk_games_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_games_winner FOREIGN KEY (winner_id) REFERENCES players (id),
    CONSTRAINT ck_games_capacity CHECK (max_players BETWEEN 2 AND 8)
) ENGINE = InnoDB;

-- Participation d'un joueur à une partie.
CREATE TABLE game_players (
    game_id       INT UNSIGNED NOT NULL,
    player_id     INT UNSIGNED NOT NULL,
    score         INT          NOT NULL DEFAULT 0,
    status        ENUM('active','eliminated','left') NOT NULL DEFAULT 'active',
    ready         TINYINT(1)   NOT NULL DEFAULT 0,
    joined_at     DATETIME(3)  NOT NULL,
    eliminated_at DATETIME(3)  NULL,
    final_rank    TINYINT UNSIGNED NULL,
    PRIMARY KEY (game_id, player_id),
    KEY idx_gp_player (player_id),
    CONSTRAINT fk_gp_game FOREIGN KEY (game_id) REFERENCES games (id) ON DELETE CASCADE,
    CONSTRAINT fk_gp_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE = InnoDB;

-- Manches : même question et même échéance pour tous (F-07).
CREATE TABLE rounds (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    game_id     INT UNSIGNED NOT NULL,
    number      TINYINT UNSIGNED NOT NULL,
    question_id INT UNSIGNED NOT NULL,
    opened_at   DATETIME(3)  NOT NULL,
    deadline_at DATETIME(3)  NOT NULL,
    closed_at   DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rounds_game_number (game_id, number),
    CONSTRAINT fk_rounds_game FOREIGN KEY (game_id) REFERENCES games (id) ON DELETE CASCADE,
    CONSTRAINT fk_rounds_question FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE = InnoDB;

-- Réponses. La contrainte unique (round_id, player_id) empêche
-- les doubles réponses (§6.1) — l'API répond alors 409.
CREATE TABLE answers (
    id           INT UNSIGNED NOT NULL AUTO_INCREMENT,
    round_id     INT UNSIGNED NOT NULL,
    player_id    INT UNSIGNED NOT NULL,
    choice_index TINYINT      NOT NULL,
    received_at  DATETIME(3)  NOT NULL,
    is_correct   TINYINT(1)   NULL,
    response_ms  INT UNSIGNED NULL,
    points       INT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_answers_round_player (round_id, player_id),
    CONSTRAINT fk_answers_round FOREIGN KEY (round_id) REFERENCES rounds (id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT ck_answers_choice CHECK (choice_index BETWEEN 0 AND 3)
) ENGINE = InnoDB;
