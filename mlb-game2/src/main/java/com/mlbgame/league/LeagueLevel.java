package com.mlbgame.league;

import com.mlbgame.common.Messages;

/**
 * 리그 레벨. 루키 → 싱글A → 더블A → 트리플A → 메이저리그 순으로 승격.
 * 마이너리그 산하팀은 실명 대신 "메이저 구단명 + 접미사"로 표시한다. 예) 디트로이트 타이거스 AA
 */
public enum LeagueLevel {
    ROOKIE("루키리그", "루키", 35),
    A("싱글A", "A", 45),
    AA("더블A", "AA", 52),
    AAA("트리플A", "AAA", 58),
    MLB("메이저리그", "", 65);

    private final String label;
    private final String teamSuffix;
    private final int averageRating;

    LeagueLevel(String label, String teamSuffix, int averageRating) {
        this.label = label;
        this.teamSuffix = teamSuffix;
        this.averageRating = averageRating;
    }

    /** 해당 레벨 선수들의 평균 능력치 (NPC 상대 생성, 출전 판정 기준) */
    public int getAverageRating() {
        return averageRating;
    }

    public String getLabel() {
        return Messages.text("LeagueLevel." + name(), label);
    }

    /** 팀명 뒤에 붙는 레벨 표기 (메이저리그는 빈 문자열) */
    public String getTeamSuffix() {
        return Messages.text("LeagueLevel." + name() + ".suffix", teamSuffix);
    }
}
