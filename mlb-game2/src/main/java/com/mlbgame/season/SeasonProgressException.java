package com.mlbgame.season;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 지금은 시리즈를 진행할 수 없음 (훈련 미선택, 시즌 종료 등) */
@ResponseStatus(HttpStatus.CONFLICT)
public class SeasonProgressException extends RuntimeException {

    public SeasonProgressException(String message) {
        super(message);
    }
}
