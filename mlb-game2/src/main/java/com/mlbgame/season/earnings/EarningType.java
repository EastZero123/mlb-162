package com.mlbgame.season.earnings;

import com.mlbgame.common.Messages;

public enum EarningType {
    SIGNING_BONUS("계약금"),
    SALARY("연봉"),
    AWARD_BONUS("수상 보너스");

    private final String label;

    EarningType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Messages.text("EarningType." + name(), label);
    }
}
