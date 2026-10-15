package com.mlbgame.training;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 훈련할 수 없는 능력치 (포지션에 없거나 이미 100) */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTrainingException extends RuntimeException {

    public InvalidTrainingException(String message) {
        super(message);
    }
}
