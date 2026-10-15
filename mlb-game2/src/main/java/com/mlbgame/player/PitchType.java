package com.mlbgame.player;

public enum PitchType {
    FOUR_SEAM("포심"),
    SINKER("싱커"),
    CUTTER("커터"),
    SLIDER("슬라이더"),
    CURVE("커브"),
    CHANGEUP("체인지업"),
    SPLITTER("스플리터"),
    KNUCKLE("너클볼");

    private final String label;

    PitchType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 구종 2~4 슬롯에서 고를 수 있는 구종 (포심은 고정 슬롯) */
    public boolean isSelectable() {
        return this != FOUR_SEAM;
    }
}
