package com.mlbgame.player;

import com.mlbgame.common.Messages;

public enum Hand {
    R("우"),
    L("좌"),
    S("양");   // 스위치 — 타석에서만 허용

    private final String label;

    Hand(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Messages.text("Hand." + name(), label);
    }
}
