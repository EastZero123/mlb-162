-- 유저가 생성한 선수
CREATE TABLE players (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    name           VARCHAR(20) NOT NULL,
    uniform_number INT         NOT NULL,
    position       VARCHAR(5)  NOT NULL,
    bats_hand      VARCHAR(5)  NOT NULL,
    throws_hand    VARCHAR(5)  NOT NULL,
    education      VARCHAR(20) NOT NULL,
    age            INT         NOT NULL,
    stage          VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    league_level   VARCHAR(10),
    team_id        BIGINT,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_players_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_players_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

CREATE INDEX idx_players_user_status ON players (user_id, status);

-- 능력치 (선수당 11행, 1~100)
CREATE TABLE player_attributes (
    player_id  BIGINT      NOT NULL,
    attr_code  VARCHAR(30) NOT NULL,
    attr_value INT         NOT NULL,
    PRIMARY KEY (player_id, attr_code),
    CONSTRAINT fk_player_attributes_player FOREIGN KEY (player_id) REFERENCES players (id)
);

-- 투수 구종 2~4 슬롯에 선택한 구종 (구종 능력치 값은 player_attributes 의 PITCH_2~4)
CREATE TABLE player_pitches (
    player_id  BIGINT      NOT NULL,
    slot       INT         NOT NULL,
    pitch_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (player_id, slot),
    CONSTRAINT fk_player_pitches_player FOREIGN KEY (player_id) REFERENCES players (id)
);
