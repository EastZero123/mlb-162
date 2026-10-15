-- 시리즈 기록: 시드 + 입력 스냅샷으로 상세 내용(타석·하이라이트)을 재계산하고, 요약만 저장한다
CREATE TABLE series_records (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    player_id    BIGINT      NOT NULL,
    season_year  INT         NOT NULL,
    series_no    INT         NOT NULL,
    seed         BIGINT      NOT NULL,
    league_level VARCHAR(10) NOT NULL,
    input_json   VARCHAR(4000) NOT NULL, -- SeriesInput 전체 (선수 능력치 스냅샷, 팀 전력, 피로·등판 상태)
    wins         INT         NOT NULL,
    losses       INT         NOT NULL,
    summary_json VARCHAR(4000) NOT NULL, -- 내 선수 시리즈 기록 합계, 컨디션, 부상
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_series_records_player_series UNIQUE (player_id, season_year, series_no),
    CONSTRAINT fk_series_records_player FOREIGN KEY (player_id) REFERENCES players (id)
);
