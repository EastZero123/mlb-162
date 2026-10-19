package com.mlbgame.league.world;

import com.mlbgame.common.Messages;

/** 1군 로스터 내 역할 (구단당 26명: 주전 9 + 벤치 4 + 선발 5 + 불펜 7 + 마무리 1) */
public enum RosterRole {
    LINEUP("주전", 9),
    BENCH("벤치", 4),
    ROTATION("선발", 5),
    BULLPEN("불펜", 7),
    CLOSER("마무리", 1);

    public static final int ROSTER_SIZE = 26;

    private final String label;
    private final int slots;

    RosterRole(String label, int slots) {
        this.label = label;
        this.slots = slots;
    }

    public String getLabel() {
        return Messages.text("RosterRole." + name(), label);
    }

    public int getSlots() {
        return slots;
    }
}
