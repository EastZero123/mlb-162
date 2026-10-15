-- 포스트시즌 진행 상태 (대진표·결과는 JSON, 선수·연도당 1행)
CREATE TABLE postseasons (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    player_id              BIGINT        NOT NULL,
    season_year            INT           NOT NULL,
    status                 VARCHAR(20)   NOT NULL,   -- IN_PROGRESS / DONE
    next_round             INT           NOT NULL,   -- 1 와일드카드 ~ 4 월드시리즈, 5 = 종료
    user_team_qualified    BOOLEAN       NOT NULL,
    user_plays             BOOLEAN       NOT NULL,   -- 내 선수가 메이저 소속이라 직접 뛰는지
    bracket_json           VARCHAR(8000) NOT NULL,
    batting_json           VARCHAR(2000),
    pitching_json          VARCHAR(2000),
    fatigue                INT           NOT NULL,
    games_since_start      INT           NOT NULL,
    consecutive_days       INT           NOT NULL,
    last_pitches           INT           NOT NULL,
    games_since_appearance INT           NOT NULL,
    injured                BOOLEAN       NOT NULL,
    champion_team_id       BIGINT,
    version                BIGINT        NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_postseasons UNIQUE (player_id, season_year),
    CONSTRAINT fk_postseasons_player FOREIGN KEY (player_id) REFERENCES players (id)
);
