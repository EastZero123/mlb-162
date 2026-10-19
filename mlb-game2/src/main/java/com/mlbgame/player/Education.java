package com.mlbgame.player;

import com.mlbgame.common.Messages;

public enum Education {
    HIGH_SCHOOL("고졸", 18),
    COLLEGE("대졸", 22);

    private final String label;
    private final int startAge;

    Education(String label, int startAge) {
        this.label = label;
        this.startAge = startAge;
    }

    public String getLabel() {
        return Messages.text("Education." + name(), label);
    }

    public int getStartAge() {
        return startAge;
    }
}
