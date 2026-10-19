package com.mlbgame.player;

import com.mlbgame.common.Messages;

public enum Nationality {
    USA("미국"),
    JAPAN("일본"),
    TAIWAN("대만"),
    KOREA("한국");

    private final String label;

    Nationality(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Messages.text("Nationality." + name(), label);
    }
}
