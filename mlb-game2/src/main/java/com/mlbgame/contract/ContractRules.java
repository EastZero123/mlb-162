package com.mlbgame.contract;

import com.mlbgame.league.world.Market;
import com.mlbgame.league.world.Payroll;
import com.mlbgame.reputation.ReputationRules;

/**
 * 계약·연봉 규칙 (순수 계산). 설계: docs/league-world.md 8장
 *
 * <pre>
 * 서비스 연수   메이저에서 보낸 시리즈 46개 = 1년 (MLB 172일/186일 근사), 마이너 기간 제외
 * 평가 OVR      OVR + min(4, 지난 시즌 수상 포인트 / 2)
 * 적정 연봉     연봉조정: 시장가(평가 OVR) × 40/60/80% (3·4·5년 차) / FA: 시장가
 * 연봉조정      구단 제시 = 적정 × 90%. 청문회는 적정 연봉에 더 가까운 쪽 금액 (같으면 구단)
 * 연장 제안     서비스 2~5년, 평가 OVR 68 이상이면 35% — 연봉 = 시장가 × 75%, 3~6년
 * FA 역제안     요구액이 오퍼보다 x% 높으면 수락 확률 1 − 3x × 평판 보정 (평판 50: 10% → 70%, 33% 이상 → 0)
 * 평판         연장 제안 확률 × 평판/50 (최대 60%), FA 오퍼 구단 수·금액 보정 — {@link ReputationRules}
 * 거부 조항     어떤 제안이든 계약할 때 트레이드 거부 조항을 넣을지 고른다 (조건·대가 없음)
 * </pre>
 */
public final class ContractRules {

    public static final int SERIES_PER_SERVICE_YEAR = 46;
    public static final double ARBITRATION_OFFER_RATE = 0.9;
    public static final double EXTENSION_CHANCE = 0.35;
    public static final int EXTENSION_MIN_OVR = 68;
    public static final double EXTENSION_DISCOUNT = 0.75;
    static final double[] ARBITRATION_SHARE = {0.4, 0.6, 0.8};

    private ContractRules() {
    }

    public static int serviceYears(int totalMlbSeries) {
        return totalMlbSeries / SERIES_PER_SERVICE_YEAR;
    }

    public static double valueOverall(double overall, int lastSeasonAwardPoints) {
        return overall + Math.min(4, lastSeasonAwardPoints / 2.0);
    }

    public static long fairArbitration(int serviceYears, double valueOverall) {
        int index = Math.max(0, Math.min(2, serviceYears - Payroll.ARBITRATION_START));
        return round(Math.max(Payroll.MINIMUM_SALARY, Payroll.marketValue(valueOverall) * ARBITRATION_SHARE[index]));
    }

    public static long arbitrationOffer(long fair) {
        return Math.max(Payroll.MINIMUM_SALARY, round(fair * ARBITRATION_OFFER_RATE));
    }

    /** 청문회: 적정 연봉에 더 가까운 쪽 (같으면 구단 제시액) */
    public static long hearing(long ask, long offer, long fair) {
        return Math.abs(ask - fair) < Math.abs(offer - fair) ? ask : offer;
    }

    public static int extensionYears(int serviceYears) {
        return Math.max(3, Math.min(6, Payroll.FREE_AGENCY_SERVICE - serviceYears + 2));
    }

    public static long extensionSalary(double valueOverall) {
        return round(Math.max(Payroll.MINIMUM_SALARY, Payroll.marketValue(valueOverall) * EXTENSION_DISCOUNT));
    }

    /** FA 오퍼 연봉: 시장가 × (0.85~1.2 운) × 재정 규모 */
    public static long freeAgentSalary(double valueOverall, Market market, double luck) {
        double factor = switch (market) {
            case BIG -> 1.1;
            case MID -> 1.0;
            case SMALL -> 0.9;
        };
        return round(Math.max(Payroll.MINIMUM_SALARY, Payroll.marketValue(valueOverall) * (0.85 + 0.35 * luck) * factor));
    }

    public static int freeAgentYears(int age, double luck) {
        if (age <= 29) return 3 + (int) (luck * 3);       // 3~5년
        if (age <= 33) return 2 + (int) (luck * 2);       // 2~3년
        return 1;
    }

    /** FA 역제안 수락 확률 (평판 50 기준) */
    public static double counterAcceptance(long ask, long offer) {
        return counterAcceptance(ask, offer, ReputationRules.START);
    }

    /** FA 역제안 수락 확률: 1 − 초과율 × 3 × 평판 보정 (평판 100 이면 결렬 위험 절반, 0 이면 1.5배) */
    public static double counterAcceptance(long ask, long offer, int reputation) {
        if (ask <= offer) {
            return 1;
        }
        double over = (double) ask / offer - 1;
        return Math.max(0, Math.min(1, 1 - over * 3 * ReputationRules.counterRiskFactor(reputation)));
    }

    static long round(double value) {
        return Math.round(value / 10_000) * 10_000;
    }
}
