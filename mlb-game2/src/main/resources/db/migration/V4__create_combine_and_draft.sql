-- 컴바인 종목별 기록
CREATE TABLE combine_results (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    player_id    BIGINT      NOT NULL,
    test_type    VARCHAR(30) NOT NULL,
    score        INT         NOT NULL,
    record_value DOUBLE      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_combine_results_player_test UNIQUE (player_id, test_type),
    CONSTRAINT fk_combine_results_player FOREIGN KEY (player_id) REFERENCES players (id)
);

-- 컴바인 종합 평가 (선수당 1행)
CREATE TABLE combine_summaries (
    player_id         BIGINT       NOT NULL,
    day_condition     VARCHAR(10)  NOT NULL,
    interview_answer  VARCHAR(20)  NOT NULL,
    scout_preference  VARCHAR(20)  NOT NULL,
    interview_bonus   INT          NOT NULL,
    age_bonus         INT          NOT NULL,
    total_score       DOUBLE       NOT NULL,
    grade             VARCHAR(2)   NOT NULL,
    scout_comment     VARCHAR(255) NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_combine_summaries_player FOREIGN KEY (player_id) REFERENCES players (id)
);

-- 드래프트 결과 (선수당 1행)
CREATE TABLE draft_results (
    player_id     BIGINT      NOT NULL,
    draft_round   INT         NOT NULL,
    pick_in_round INT         NOT NULL,
    overall_pick  INT         NOT NULL,
    team_id       BIGINT      NOT NULL,
    signing_bonus BIGINT      NOT NULL,
    start_level   VARCHAR(10) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_draft_results_player FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT fk_draft_results_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
