package com.mlbgame.league.world;

import com.mlbgame.league.LeagueLevel;

/**
 * MLB 연봉 규칙 (NPC·내 선수 공통). 설계: docs/league-world.md 7~8장
 * 수치는 {@link PayrollRules#MLB} 에 있고, 모드에 따라 달라지는 곳은 규칙셋의 {@link PayrollRules} 를 쓴다.
 */
public final class Payroll {

    public static final long MINIMUM_SALARY = PayrollRules.MLB.minimumSalary();
    public static final long LUXURY_TAX_THRESHOLD = PayrollRules.MLB.luxuryTaxThreshold();
    public static final int ARBITRATION_START = PayrollRules.MLB.arbitrationStart();
    public static final int FREE_AGENCY_SERVICE = PayrollRules.MLB.freeAgencyService();

    private Payroll() {
    }

    /** 시장가 = $8M × e^(0.075 × (OVR − 65)), 1만 달러 단위, 최저 연봉 이상 */
    public static long marketValue(double overall) {
        return PayrollRules.MLB.marketValue(overall);
    }

    /** 서비스 연수 기준 연봉 (FA 계약은 별도로 시장가 × 변동) */
    public static long salaryByService(int serviceYears, double overall) {
        return PayrollRules.MLB.salaryByService(serviceYears, overall);
    }

    /** 레벨별 마이너리그 시즌 연봉 */
    public static long minorLeagueSalary(LeagueLevel level) {
        return PayrollRules.MLB.levelSalary(level);
    }
}
