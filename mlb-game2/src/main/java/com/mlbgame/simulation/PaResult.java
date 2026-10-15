package com.mlbgame.simulation;

/** 타석 결과 */
public enum PaResult {
    STRIKEOUT,
    WALK,
    HIT_BY_PITCH,
    SINGLE,
    DOUBLE,
    TRIPLE,
    HOME_RUN,
    GROUND_OUT,
    FLY_OUT;

    public boolean isHit() {
        return this == SINGLE || this == DOUBLE || this == TRIPLE || this == HOME_RUN;
    }

    /** 타수에 포함되는지 (볼넷·몸에 맞는 공 제외. 희생타는 경기 진행 단계에서 따로 처리) */
    public boolean isAtBat() {
        return this != WALK && this != HIT_BY_PITCH;
    }

    public boolean isOut() {
        return this == STRIKEOUT || this == GROUND_OUT || this == FLY_OUT;
    }

    public int totalBases() {
        return switch (this) {
            case SINGLE -> 1;
            case DOUBLE -> 2;
            case TRIPLE -> 3;
            case HOME_RUN -> 4;
            default -> 0;
        };
    }
}
