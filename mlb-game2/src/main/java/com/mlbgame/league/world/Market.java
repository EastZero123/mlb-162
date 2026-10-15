package com.mlbgame.league.world;

import java.util.Set;

/** 구단 재정 규모 — FA 영입력과 기본 예산을 정한다 (잘 바뀌지 않는 성향) */
public enum Market {
    BIG("빅마켓", 230_000_000L, 2),
    MID("중간", 160_000_000L, 0),
    SMALL("스몰마켓", 100_000_000L, -2);

    /** 실제 시장 규모를 흉내 낸 기본 배정 */
    static final Set<String> BIG_MARKET_TEAMS = Set.of("NYY", "LAD", "NYM", "BOS", "PHI", "CHC", "SF", "LAA");
    static final Set<String> SMALL_MARKET_TEAMS = Set.of("TB", "ATH", "MIA", "PIT", "KC", "CLE", "MIL", "CIN");

    private final String label;
    private final long baseBudget;
    /** 초기 로스터 생성 시 팀 전력 보정 (능력치 단위) */
    private final int talentBonus;

    Market(String label, long baseBudget, int talentBonus) {
        this.label = label;
        this.baseBudget = baseBudget;
        this.talentBonus = talentBonus;
    }

    public static Market of(String teamCode) {
        if (BIG_MARKET_TEAMS.contains(teamCode)) return BIG;
        if (SMALL_MARKET_TEAMS.contains(teamCode)) return SMALL;
        return MID;
    }

    public String getLabel() {
        return label;
    }

    public long getBaseBudget() {
        return baseBudget;
    }

    public int getTalentBonus() {
        return talentBonus;
    }
}
