package com.mlbgame.player;

import com.mlbgame.common.Messages;

/** 출신 학교는 실제 학교명 대신 지역 단위로만 기록한다 */
public enum SchoolRegion {
    US_EAST("미국 동부"),
    US_WEST("미국 서부"),
    US_SOUTH("미국 남부"),
    US_NORTH("미국 북부"),
    OVERSEAS("해외");

    private final String label;

    SchoolRegion(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Messages.text("SchoolRegion." + name(), label);
    }
}
