package com.mlbgame.player;

/** 생성 시 능력치 배분이 규칙(총량·능력치당 한도)에 맞지 않음 */
public class InvalidAllocationException extends RuntimeException {

    public InvalidAllocationException(String message) {
        super(message);
    }
}
