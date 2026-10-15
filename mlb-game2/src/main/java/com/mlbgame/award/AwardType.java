package com.mlbgame.award;

/**
 * 상 종류. 포인트 = 스탯업 기회 수 (설계: docs/player-attributes.md 5장), 보너스 = 수상 보너스(달러, 수령 원장).
 */
public enum AwardType {
    // 월간 (각 +1)
    PLAYER_OF_MONTH("이달의 선수", 1, 25_000),
    PITCHER_OF_MONTH("이달의 투수", 1, 25_000),
    ROOKIE_OF_MONTH("이달의 루키", 1, 15_000),
    RELIEVER_OF_MONTH("이달의 구원투수", 1, 15_000),
    MINOR_PLAYER_OF_MONTH("마이너리그 이달의 선수", 1, 2_000),

    // 시즌 중
    ALL_STAR("올스타 선정", 1, 100_000),
    ALL_STAR_MVP("올스타전 MVP", 1, 50_000),
    MINOR_ALL_STAR("마이너리그 올스타", 1, 2_000),

    // 시즌 종료
    MVP("MVP", 5, 500_000),
    CY_YOUNG("사이영상", 5, 500_000),
    ROOKIE_OF_YEAR("신인왕", 3, 150_000),
    SILVER_SLUGGER("실버슬러거", 2, 100_000),
    GOLD_GLOVE("골드글러브", 2, 100_000),
    RELIEVER_OF_YEAR("올해의 구원투수", 2, 100_000),
    MINOR_PLAYER_OF_YEAR("마이너리그 올해의 선수", 2, 10_000),

    // 포스트시즌 (M4-4)
    LCS_MVP("리그 챔피언십시리즈 MVP", 2, 100_000),
    WORLD_SERIES_MVP("월드시리즈 MVP", 4, 300_000);

    private final String label;
    private final int points;
    private final long bonus;

    AwardType(String label, int points, long bonus) {
        this.label = label;
        this.points = points;
        this.bonus = bonus;
    }

    public String getLabel() {
        return label;
    }

    public int getPoints() {
        return points;
    }

    public long getBonus() {
        return bonus;
    }
}
