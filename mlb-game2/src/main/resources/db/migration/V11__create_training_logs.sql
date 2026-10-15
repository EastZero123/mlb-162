-- 훈련 기록: 어느 시리즈 뒤에 어떤 능력치를 올렸는지 (커리어 성장 이력, 자동 훈련 도입 시 출처 구분)
CREATE TABLE training_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    player_id   BIGINT      NOT NULL,
    season_year INT         NOT NULL,
    series_no   INT         NOT NULL,   -- 이 시리즈를 마친 뒤의 훈련
    attr_code   VARCHAR(30) NOT NULL,
    value_after INT         NOT NULL,
    source      VARCHAR(10) NOT NULL,   -- MANUAL / AUTO
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_training_logs UNIQUE (player_id, season_year, series_no),
    CONSTRAINT fk_training_logs_player FOREIGN KEY (player_id) REFERENCES players (id)
);
