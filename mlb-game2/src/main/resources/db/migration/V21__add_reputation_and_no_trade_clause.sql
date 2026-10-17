-- 평판 (0~100, 기존 선수 포함 50에서 시작): 트레이드 요청 성공률·자동 트레이드·역제안·FA 오퍼·연장 제안에 반영
ALTER TABLE players ADD COLUMN reputation INT NOT NULL DEFAULT 50;

-- 트레이드 거부 조항: 현재 계약에 있으면 구단 주도 트레이드(루머 포함)가 없다. 직접 요청한 트레이드는 가능
ALTER TABLE players ADD COLUMN no_trade_clause BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE contract_offers ADD COLUMN no_trade_clause BOOLEAN NOT NULL DEFAULT FALSE;

-- 평판 변화 이력 (화면의 "최근 평판 변화")
CREATE TABLE reputation_logs (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    player_id        BIGINT       NOT NULL,
    season_year      INT          NOT NULL,
    delta            INT          NOT NULL,
    reputation_after INT          NOT NULL,
    reason           VARCHAR(100) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reputation_logs_player FOREIGN KEY (player_id) REFERENCES players (id)
);
