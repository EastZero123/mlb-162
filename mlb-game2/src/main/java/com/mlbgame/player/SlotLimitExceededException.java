package com.mlbgame.player;

import com.mlbgame.common.Messages;
import com.mlbgame.league.LeagueMode;

public class SlotLimitExceededException extends RuntimeException {

    public SlotLimitExceededException(int limit) {
        super(Messages.get("create.error.slotLimit", limit));
    }

    public SlotLimitExceededException(int limit, LeagueMode mode) {
        super(Messages.get("create.error.slotLimitMode", mode.getLabel(), limit));
    }
}
