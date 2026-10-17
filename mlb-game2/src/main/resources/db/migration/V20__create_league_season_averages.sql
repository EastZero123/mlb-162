-- 시즌별 메이저리그 NPC 기록 합계 (wRC+·OPS+·FIP·ERA+·WAR 의 리그 평균 기준)
-- NPC 기록은 저장하지 않고 월드 시드로 다시 만들지만, 시즌이 넘어가면 로스터가 바뀌어 지난 시즌을 다시 만들 수 없으므로
-- 시즌 전환 직전에 그해 합계를 남겨 둔다.
CREATE TABLE league_season_averages (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    world_id            BIGINT      NOT NULL,
    season_year         INT         NOT NULL,
    plate_appearances   BIGINT      NOT NULL,
    at_bats             BIGINT      NOT NULL,
    hits                BIGINT      NOT NULL,
    doubles             BIGINT      NOT NULL,
    triples             BIGINT      NOT NULL,
    home_runs           BIGINT      NOT NULL,
    walks               BIGINT      NOT NULL,
    hit_by_pitch        BIGINT      NOT NULL,
    sac_flies           BIGINT      NOT NULL,
    runs                BIGINT      NOT NULL,
    pitching_outs       BIGINT      NOT NULL,
    pitching_runs       BIGINT      NOT NULL,
    pitching_home_runs  BIGINT      NOT NULL,
    pitching_walks      BIGINT      NOT NULL,
    pitching_strikeouts BIGINT      NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_league_season_averages UNIQUE (world_id, season_year),
    CONSTRAINT fk_league_season_averages_world FOREIGN KEY (world_id) REFERENCES league_worlds (id)
);
