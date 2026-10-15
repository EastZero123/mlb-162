package com.mlbgame.player;

public enum Position {
    SP("SP", "선발투수", true),
    CP("CP", "마무리투수", true),
    /** 불펜투수 — NPC 전용 (유저는 선택 불가) */
    RP("RP", "불펜투수", true),
    C("C", "포수", false),
    B1("1B", "1루수", false),
    B2("2B", "2루수", false),
    B3("3B", "3루수", false),
    SS("SS", "유격수", false),
    OF("OF", "외야수", false);

    private final String shortName;
    private final String label;
    private final boolean pitcher;

    Position(String shortName, String label, boolean pitcher) {
        this.shortName = shortName;
        this.label = label;
        this.pitcher = pitcher;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPitcher() {
        return pitcher;
    }

    /** 유저가 선수 생성 시 고를 수 있는 포지션인지 */
    public boolean isUserSelectable() {
        return this != RP;
    }
}
