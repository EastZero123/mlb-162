package com.mlbgame.player;

import com.mlbgame.common.Messages;

public class SlotLimitExceededException extends RuntimeException {

    public SlotLimitExceededException(int limit) {
        super(Messages.get("create.error.slotLimit", limit));
    }
}
