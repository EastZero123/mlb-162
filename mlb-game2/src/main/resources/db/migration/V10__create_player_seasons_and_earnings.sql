-- 내 선수의 시즌 진행 상태 + 시즌 누적 기록
CREATE TABLE player_seasons (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    player_id                BIGINT        NOT NULL,
    season_year              INT           NOT NULL,
    league_level             VARCHAR(10)   NOT NULL,
    team_id                  BIGINT        NOT NULL,
    next_series_no           INT           NOT NULL,   -- 다음에 진행할 시리즈 (55 = 시즌 종료)
    fatigue                  INT           NOT NULL,
    games_since_start        INT           NOT NULL,   -- 투수 등판 상태 (PitcherUsage)
    consecutive_days         INT           NOT NULL,
    last_pitches             INT           NOT NULL,
    games_since_appearance   INT           NOT NULL,
    injured_series_remaining INT           NOT NULL,   -- 부상 결장 남은 시리즈
    injury_severity          VARCHAR(20),
    training_pending         BOOLEAN       NOT NULL,   -- 훈련을 골라야 다음 시리즈 진행 가능
    team_wins                INT           NOT NULL,
    team_losses              INT           NOT NULL,
    status                   VARCHAR(20)   NOT NULL,   -- IN_PROGRESS / COMPLETED
    batting_json             VARCHAR(2000),
    pitching_json            VARCHAR(2000),
    fielding_json            VARCHAR(2000),
    version                  BIGINT        NOT NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_player_seasons UNIQUE (player_id, season_year),
    CONSTRAINT fk_player_seasons_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_player_seasons_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

-- 수령 원장: 계약금·연봉·수상 보너스 (누적 연봉 랭킹용, 연도·종류별 1행에 누적)
CREATE TABLE player_earnings (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    player_id    BIGINT      NOT NULL,
    season_year  INT         NOT NULL,
    earning_type VARCHAR(20) NOT NULL,   -- SIGNING_BONUS / SALARY / AWARD_BONUS
    amount       BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_player_earnings UNIQUE (player_id, season_year, earning_type),
    CONSTRAINT fk_player_earnings_player FOREIGN KEY (player_id) REFERENCES players (id)
);
