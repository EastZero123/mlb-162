package com.mlbgame.player;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 선수 진행 단계가 맞지 않는 요청 (예: 컴바인을 두 번 진행) */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidStageException extends RuntimeException {

    public InvalidStageException(String message) {
        super(message);
    }
}
