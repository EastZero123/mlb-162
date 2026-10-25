-- 게임 모드(MLB/KBO). 기존 데이터는 모두 MLB. 설계: docs/kbo-mode.md
ALTER TABLE teams ADD COLUMN league_mode VARCHAR(5) NOT NULL DEFAULT 'MLB';
ALTER TABLE players ADD COLUMN league_mode VARCHAR(5) NOT NULL DEFAULT 'MLB';
ALTER TABLE league_worlds ADD COLUMN league_mode VARCHAR(5) NOT NULL DEFAULT 'MLB';
ALTER TABLE ranking_entries ADD COLUMN league_mode VARCHAR(5) NOT NULL DEFAULT 'MLB';
