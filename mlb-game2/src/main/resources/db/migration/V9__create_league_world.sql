-- 리그 월드: 내 선수 커리어마다 별도로 생성 (설계: docs/league-world.md)
CREATE TABLE league_worlds (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    player_id    BIGINT      NOT NULL,
    seed         BIGINT      NOT NULL,
    current_year INT         NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_league_worlds_player UNIQUE (player_id),
    CONSTRAINT fk_league_worlds_player FOREIGN KEY (player_id) REFERENCES players (id)
);

-- 월드 안의 구단 성향·재정
CREATE TABLE world_teams (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    world_id         BIGINT      NOT NULL,
    team_id          BIGINT      NOT NULL,
    market           VARCHAR(10) NOT NULL,   -- BIG / MID / SMALL
    spending_percent INT         NOT NULL,   -- 구단주 투자 성향 85~115
    farm_score       INT         NOT NULL,   -- 팜 시스템 점수 1~100
    budget           BIGINT      NOT NULL,   -- 연간 예산 (달러)
    PRIMARY KEY (id),
    CONSTRAINT uk_world_teams UNIQUE (world_id, team_id),
    CONSTRAINT fk_world_teams_world FOREIGN KEY (world_id) REFERENCES league_worlds (id),
    CONSTRAINT fk_world_teams_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

-- NPC 선수 (메이저 1군 + 한 번이라도 1군에 올라온 선수)
CREATE TABLE npc_players (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    world_id       BIGINT      NOT NULL,
    team_id        BIGINT,                  -- FA·은퇴면 NULL
    name           VARCHAR(40) NOT NULL,
    position       VARCHAR(5)  NOT NULL,
    bats_hand      VARCHAR(5)  NOT NULL,
    throws_hand    VARCHAR(5)  NOT NULL,
    age            INT         NOT NULL,
    potential      INT         NOT NULL,    -- 숨김 잠재력
    attribute_values VARCHAR(60) NOT NULL,  -- 포지션 능력치 11개를 표시 순서대로 쉼표 구분
    roster_role    VARCHAR(10) NOT NULL,    -- LINEUP / BENCH / ROTATION / BULLPEN / CLOSER
    status         VARCHAR(10) NOT NULL,    -- MLB / MINORS / FA / RETIRED
    salary         BIGINT      NOT NULL,
    contract_years INT         NOT NULL,
    service_years  INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_npc_players_world FOREIGN KEY (world_id) REFERENCES league_worlds (id),
    CONSTRAINT fk_npc_players_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

CREATE INDEX idx_npc_players_world_team ON npc_players (world_id, team_id, status);
