package com.mlbgame.trade;

import com.mlbgame.league.LeagueLevel;

/**
 * 트레이드 이벤트 규칙 (설계: requirements.md FR-CAR-07).
 *
 * <pre>
 * 기간        6번째 ~ 36번째 시리즈 뒤 (7월 말 트레이드 마감), 메이저·트리플A 소속만
 * 시리즈당 확률  1.2% × (소속팀 승률 .450 미만이면 ×2) × (OVR 70 이상 ×1.5)
 *                × (연봉 $15M 이상 & 구단 예산 초과 ×2) × (트레이드 요청 거절 이력 ×4), 최대 30%
 * 단계        처음 걸리면 루머, 루머가 있는 상태에서 또 걸리면 트레이드 성사
 * 행선지      승률 .520 이상 경쟁 팀 중 (승률 − .5 + 예산 여유) 가중 무작위, 없으면 아무 팀
 * 트레이드 요청  시즌당 1회, 마감 전까지 — 30% 즉시 성사, 실패하면 거절 이력이 남아 이후 트레이드 확률 ×4
 * </pre>
 */
public final class TradeRules {

    public static final int FIRST_SERIES = 6;
    public static final int DEADLINE_SERIES = 36;
    public static final double BASE_CHANCE = 0.012;
    public static final double MAX_CHANCE = 0.30;
    public static final double REQUEST_SUCCESS = 0.30;
    public static final double REBUILDING_PCT = 0.450;
    public static final double CONTENDER_PCT = 0.520;
    public static final long EXPENSIVE_SALARY = 15_000_000L;

    private TradeRules() {
    }

    public static boolean inWindow(int seriesPlayed, LeagueLevel level) {
        return seriesPlayed >= FIRST_SERIES && seriesPlayed <= DEADLINE_SERIES
                && (level == LeagueLevel.MLB || level == LeagueLevel.AAA);
    }

    public static double chance(double teamWinPct, double overall, long salary, boolean overBudget, boolean requestDenied) {
        double p = BASE_CHANCE;
        if (teamWinPct < REBUILDING_PCT) p *= 2;
        if (overall >= 70) p *= 1.5;
        if (salary >= EXPENSIVE_SALARY && overBudget) p *= 2;
        if (requestDenied) p *= 4;
        return Math.min(MAX_CHANCE, p);
    }
}
