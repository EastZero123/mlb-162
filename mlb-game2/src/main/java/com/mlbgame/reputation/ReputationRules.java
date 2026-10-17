package com.mlbgame.reputation;

/**
 * 평판 규칙 (순수 계산). 평판 0~100, 시작 50. 평판 50 이면 모든 보정이 평판 도입 전과 같다.
 *
 * <pre>
 * 변화
 *   트레이드 요청 거절당함 −8 · 트레이드 요청 성사 −2
 *   FA 역제안 결렬 −5 · 연봉조정 청문회 승리 −3 (패배는 0) · 연장 제안 거절 −2 · FA 오퍼 거절 0
 *   제시 조건 그대로 수락 +2
 *   시즌 결산: 수상 포인트당 +1 (최대 +5) · 월드시리즈 우승 +5 · 메이저 주요 시즌(27시리즈 이상) +1
 * 영향
 *   트레이드 요청 성공률   30% × (0.6 + 0.8 × 평판/100)
 *   자동 트레이드 확률     평판 30 미만이면 ×1.5
 *   FA 역제안 결렬 위험    초과율 × 3 × (1.5 − 평판/100)
 *   FA 오퍼              평판 70 이상 구단 +1 · 30 미만 −1, 금액 × (0.95 + 0.1 × 평판/100)
 *   연장 제안 확률        35% × 평판/50, 최대 60%
 * 등급  80+ 존경받음 · 60+ 좋음 · 40+ 보통 · 20+ 나쁨 · 그 아래 최악
 * </pre>
 */
public final class ReputationRules {

    public static final int START = 50;

    public static final int TRADE_REQUEST_DENIED = -8;
    public static final int TRADE_REQUEST_GRANTED = -2;
    public static final int COUNTER_REJECTED = -5;
    public static final int ARBITRATION_WON = -3;
    public static final int EXTENSION_DECLINED = -2;
    public static final int ACCEPTED_AS_OFFERED = 2;
    public static final int MAX_AWARD_BONUS = 5;
    public static final int CHAMPIONSHIP = 5;
    public static final int MAJOR_SEASON = 1;

    static final int GOOD = 70;
    static final int BAD = 30;
    static final double MAX_EXTENSION_CHANCE = 0.60;

    private ReputationRules() {
    }

    public static String grade(int reputation) {
        if (reputation >= 80) return "존경받음";
        if (reputation >= 60) return "좋음";
        if (reputation >= 40) return "보통";
        if (reputation >= 20) return "나쁨";
        return "최악";
    }

    /** 트레이드 요청 성공률 보정 (평판 50 = 1.0, 0 = 0.6, 100 = 1.4) */
    public static double tradeRequestFactor(int reputation) {
        return 0.6 + 0.8 * reputation / 100.0;
    }

    /** 자동 트레이드 확률 보정 */
    public static double autoTradeFactor(int reputation) {
        return reputation < BAD ? 1.5 : 1.0;
    }

    /** FA 역제안 결렬 위험 보정 (평판 50 = 1.0, 0 = 1.5, 100 = 0.5) */
    public static double counterRiskFactor(int reputation) {
        return 1.5 - reputation / 100.0;
    }

    /** FA 오퍼 구단 수 보정 */
    public static int freeAgentOfferAdjustment(int reputation) {
        if (reputation >= GOOD) return 1;
        if (reputation < BAD) return -1;
        return 0;
    }

    /** FA 오퍼 금액 보정 (평판 50 = 1.0, 0 = 0.95, 100 = 1.05) */
    public static double freeAgentSalaryFactor(int reputation) {
        return 0.95 + 0.1 * reputation / 100.0;
    }

    /** 연장 제안 확률 */
    public static double extensionChance(double baseChance, int reputation) {
        return Math.min(MAX_EXTENSION_CHANCE, baseChance * reputation / START);
    }

    /** 시즌 결산 수상 보너스 */
    public static int awardBonus(int awardPoints) {
        return Math.min(MAX_AWARD_BONUS, Math.max(0, awardPoints));
    }
}
