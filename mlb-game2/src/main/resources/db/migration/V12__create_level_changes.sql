-- 승격·강등 이력
CREATE TABLE level_changes (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    player_id    BIGINT      NOT NULL,
    season_year  INT         NOT NULL,
    after_series INT         NOT NULL,   -- 이 시리즈를 마친 뒤 이동
    from_level   VARCHAR(10) NOT NULL,
    to_level     VARCHAR(10) NOT NULL,
    score        DOUBLE      NOT NULL,   -- 판정 점수 (OVR 차이 + 2 × 성적 점수)
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_level_changes_player FOREIGN KEY (player_id) REFERENCES players (id)
);

CREATE INDEX idx_level_changes_player ON level_changes (player_id, season_year);

-- 메이저리그에서 보낸 시리즈 수 (서비스 연수 계산용)
ALTER TABLE player_seasons ADD COLUMN mlb_series_played INT NOT NULL DEFAULT 0;
