-- 트레이드 이벤트: 루머 / 트레이드 / 트레이드 요청 / 요청 거절
CREATE TABLE trade_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    player_id    BIGINT       NOT NULL,
    season_year  INT          NOT NULL,
    after_series INT          NOT NULL,
    kind         VARCHAR(20)  NOT NULL,   -- RUMOR / TRADE / REQUEST_DENIED
    from_team_id BIGINT       NOT NULL,
    to_team_id   BIGINT,
    note         VARCHAR(255),
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_trade_events_player FOREIGN KEY (player_id) REFERENCES players (id)
);

CREATE INDEX idx_trade_events_player ON trade_events (player_id, season_year);
