-- 시상 내역 (상 1개 = 스탯업 포인트 + 수상 보너스)
CREATE TABLE awards (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    player_id    BIGINT      NOT NULL,
    season_year  INT         NOT NULL,
    after_series INT         NOT NULL,   -- 이 시리즈를 마친 뒤 수상 (월간 9·18·…, 올스타 27, 시즌 54)
    award_type   VARCHAR(30) NOT NULL,
    points       INT         NOT NULL,
    bonus        BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_awards UNIQUE (player_id, season_year, after_series, award_type),
    CONSTRAINT fk_awards_player FOREIGN KEY (player_id) REFERENCES players (id)
);

-- 보유 스탯업 포인트 (시상으로 적립, 원할 때 능력치 +1 로 사용, 시즌 넘어 이월)
ALTER TABLE players ADD COLUMN stat_points INT NOT NULL DEFAULT 0;

-- 포인트 사용 기록
CREATE TABLE stat_point_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    player_id   BIGINT      NOT NULL,
    attr_code   VARCHAR(30) NOT NULL,
    value_after INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stat_point_logs_player FOREIGN KEY (player_id) REFERENCES players (id)
);
