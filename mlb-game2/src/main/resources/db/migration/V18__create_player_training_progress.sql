-- 훈련 진행도: 능력치가 높을수록·나이가 많을수록 +1 에 훈련이 여러 번 필요하다 (M5-3 밸런스 조정)
-- 진행도가 0 인 능력치는 행을 두지 않는다
CREATE TABLE player_training_progress (
    player_id  BIGINT      NOT NULL,
    attr_code  VARCHAR(30) NOT NULL,
    progress   INT         NOT NULL,
    PRIMARY KEY (player_id, attr_code),
    CONSTRAINT fk_player_training_progress_player FOREIGN KEY (player_id) REFERENCES players (id)
);
