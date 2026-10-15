-- MySQL 8 에서 root(또는 관리자) 계정으로 1회 실행
-- 비밀번호는 원하는 값으로 바꾼 뒤 application-local.yml 에도 동일하게 기입
CREATE DATABASE IF NOT EXISTS mlb_game
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'mlb_game'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON mlb_game.* TO 'mlb_game'@'%';
FLUSH PRIVILEGES;
