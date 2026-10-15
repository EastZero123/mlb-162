-- 랭킹: 은퇴 순간의 커리어 스냅샷 (선수당 1행, 이후 변경 없음)
CREATE TABLE ranking_entries (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    player_id         BIGINT      NOT NULL,
    user_id           BIGINT      NOT NULL,
    nickname          VARCHAR(20) NOT NULL,
    player_name       VARCHAR(20) NOT NULL,
    position          VARCHAR(5)  NOT NULL,
    final_age         INT         NOT NULL,
    seasons           INT         NOT NULL,
    retired_at        DATETIME(6) NOT NULL,   -- 월간 필터 기준 (한국 시간)
    career_earnings   BIGINT      NOT NULL,
    hall_of_fame      INT         NOT NULL,
    award_points      INT         NOT NULL,
    mlb_games         INT         NOT NULL,
    mlb_pa            INT         NOT NULL,
    mlb_hits          INT         NOT NULL,
    mlb_home_runs     INT         NOT NULL,
    mlb_avg           DOUBLE      NOT NULL,
    mlb_outs          INT         NOT NULL,
    mlb_wins          INT         NOT NULL,
    mlb_strikeouts    INT         NOT NULL,
    mlb_saves         INT         NOT NULL,
    mlb_era           DOUBLE      NOT NULL,
    combine_score     DOUBLE      NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ranking_entries_player UNIQUE (player_id),
    CONSTRAINT fk_ranking_entries_player FOREIGN KEY (player_id) REFERENCES players (id)
);

CREATE INDEX idx_ranking_retired_at ON ranking_entries (retired_at);
CREATE INDEX idx_ranking_earnings ON ranking_entries (career_earnings);
