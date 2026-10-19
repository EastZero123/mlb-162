package com.mlbgame.player;

import com.mlbgame.common.Messages;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 없는 선수이거나 다른 유저의 선수 — 존재 여부를 노출하지 않도록 둘 다 404 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException() {
        super(Messages.get("error.playerNotFound"));
    }
}
