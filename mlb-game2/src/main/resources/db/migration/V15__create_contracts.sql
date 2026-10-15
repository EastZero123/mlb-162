-- 내 선수 메이저 계약 (마이너 선수는 contract_type = NONE)
ALTER TABLE players ADD COLUMN salary BIGINT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN contract_years INT NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN contract_type VARCHAR(20) NOT NULL DEFAULT 'NONE';

-- 오프시즌 계약 제안 (연봉조정 / 연장 계약 / FA)
CREATE TABLE contract_offers (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    player_id   BIGINT      NOT NULL,
    season_year INT         NOT NULL,   -- 이 계약이 시작되는 시즌
    kind        VARCHAR(20) NOT NULL,   -- ARBITRATION / EXTENSION / FREE_AGENCY
    team_id     BIGINT      NOT NULL,
    years       INT         NOT NULL,
    salary      BIGINT      NOT NULL,   -- 연봉 (달러/년)
    fair_value  BIGINT      NOT NULL,   -- 적정 연봉 (청문회·역제안 판단 기준, 화면에는 숨김)
    status      VARCHAR(20) NOT NULL,   -- PENDING / ACCEPTED / REJECTED
    note        VARCHAR(255),
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_contract_offers_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_contract_offers_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

CREATE INDEX idx_contract_offers_player ON contract_offers (player_id, status);
