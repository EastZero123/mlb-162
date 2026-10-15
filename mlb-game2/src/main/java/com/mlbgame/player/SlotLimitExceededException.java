package com.mlbgame.player;

public class SlotLimitExceededException extends RuntimeException {

    public SlotLimitExceededException(int limit) {
        super("활성 선수는 계정당 " + limit + "명까지 만들 수 있습니다.");
    }
}
