package com.mlbgame.league;

/**
 * 게임 모드 = 선수가 뛰는 리그. 선수·구단·리그 월드·랭킹에 저장되고, 규칙은 모드별 {@code LeagueRuleset} 이 정한다.
 * 설계: docs/kbo-mode.md
 */
public enum LeagueMode {
    MLB("MLB"),
    KBO("KBO");

    private final String label;

    LeagueMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
