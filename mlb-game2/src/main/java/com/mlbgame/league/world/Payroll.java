package com.mlbgame.league.world;

import com.mlbgame.league.LeagueLevel;

/**
 * 연봉 규칙 (NPC·내 선수 공통). 설계: docs/league-world.md 7~8장
 */
public final class Payroll {

    public static final long MINIMUM_SALARY = 760_000L;
    public static final long LUXURY_TAX_THRESHOLD = 240_000_000L;
    /** 연봉조정 3·4·5년 차: 시장가 대비 비율 */
    static final double[] ARBITRATION_SHARE = {0.4, 0.6, 0.8};
    public static final int ARBITRATION_START = 3;
    public static final int FREE_AGENCY_SERVICE = 6;

    private Payroll() {
    }

    /** 시장가 = $8M × e^(0.075 × (OVR − 65)), 1만 달러 단위, 최저 연봉 이상 */
    public static long marketValue(double overall) {
        double value = 8_000_000 * Math.exp(0.075 * (overall - 65));
        return Math.max(MINIMUM_SALARY, Math.round(value / 10_000) * 10_000);
    }

    /** 서비스 연수 기준 연봉 (FA 계약은 별도로 시장가 × 변동) */
    public static long salaryByService(int serviceYears, double overall) {
        if (serviceYears < ARBITRATION_START) {
            return MINIMUM_SALARY;
        }
        if (serviceYears < FREE_AGENCY_SERVICE) {
            long value = Math.round(marketValue(overall) * ARBITRATION_SHARE[serviceYears - ARBITRATION_START] / 10_000) * 10_000;
            return Math.max(MINIMUM_SALARY, value);
        }
        return marketValue(overall);
    }

    /** 레벨별 마이너리그 시즌 연봉 */
    public static long minorLeagueSalary(LeagueLevel level) {
        return switch (level) {
            case ROOKIE -> 20_000L;
            case A -> 25_000L;
            case AA -> 30_000L;
            case AAA -> 40_000L;
            case MLB -> MINIMUM_SALARY;
        };
    }
}
