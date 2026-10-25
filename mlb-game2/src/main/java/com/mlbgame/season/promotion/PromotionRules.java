package com.mlbgame.season.promotion;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;

/**
 * 승격·강등 판정 (MLB 9시리즈마다). 능력치와 최근 성적을 섞는다. 수치는 모드별 {@link Params}.
 *
 * <pre>
 * 판정 점수 = (OVR − 소속 레벨 평균) + 2 × 성적 점수
 * 성적 점수 타자 (OPS − .720) / .050       (최소 40타석)
 *          선발 (4.30 − ERA) / 0.60        (최소 15이닝)
 *          마무리 (4.00 − ERA) / 0.80      (최소 5이닝)
 *          ±4 로 제한, 표본이 부족하면 0
 * 승격 +4 이상 / 강등 −8 이하, 한 번에 한 레벨
 * 레전드 예우: 명예의 전당 점수 150 이상이면 메이저 → 트리플A 강등 없음 (말년 기량 하락에도 메이저 잔류)
 * </pre>
 */
public final class PromotionRules {

    public static final int EVALUATION_INTERVAL = 9;
    static final double PERFORMANCE_WEIGHT = 2;
    static final double PERFORMANCE_CAP = 4;
    static final double PROMOTE_AT = 4;
    static final double DEMOTE_AT = -8;

    static final double BASE_OPS = 0.720;
    static final double OPS_PER_POINT = 0.050;
    static final int MIN_PLATE_APPEARANCES = 40;
    static final double STARTER_BASE_ERA = 4.30;
    static final double STARTER_ERA_PER_POINT = 0.60;
    static final int STARTER_MIN_OUTS = 45;      // 15이닝
    static final double CLOSER_BASE_ERA = 4.00;
    static final double CLOSER_ERA_PER_POINT = 0.80;
    static final int CLOSER_MIN_OUTS = 15;       // 5이닝
    public static final int LEGEND_HALL_OF_FAME_SCORE = 150;   // 메이저 강등 면제 기준

    /**
     * 모드별 판정 수치. 표본 기준(타석·아웃)은 판정 주기에 맞춰 정한다.
     *
     * @param interval 판정 주기 (시리즈)
     */
    public record Params(int interval, double performanceWeight, double performanceCap, double promoteAt,
                         double demoteAt, double baseOps, double opsPerPoint, int minPlateAppearances,
                         double starterBaseEra, double starterEraPerPoint, int starterMinOuts,
                         double closerBaseEra, double closerEraPerPoint, int closerMinOuts, int legendHallOfFameScore) {
    }

    public static final Params MLB = new Params(EVALUATION_INTERVAL, PERFORMANCE_WEIGHT, PERFORMANCE_CAP, PROMOTE_AT,
            DEMOTE_AT, BASE_OPS, OPS_PER_POINT, MIN_PLATE_APPEARANCES, STARTER_BASE_ERA, STARTER_ERA_PER_POINT,
            STARTER_MIN_OUTS, CLOSER_BASE_ERA, CLOSER_ERA_PER_POINT, CLOSER_MIN_OUTS, LEGEND_HALL_OF_FAME_SCORE);

    private PromotionRules() {
    }

    /** 레전드 예우: 메이저에서의 강등만 면제한다 (마이너 레벨 사이 강등은 그대로) */
    public static boolean isDemotionExempt(LeagueLevel from, int hallOfFameScore) {
        return isDemotionExempt(MLB, from, hallOfFameScore);
    }

    /** 레전드 예우: 최상위 레벨에서의 강등만 면제한다 */
    public static boolean isDemotionExempt(Params params, LeagueLevel from, int hallOfFameScore) {
        return from.isTop() && hallOfFameScore >= params.legendHallOfFameScore();
    }

    public enum Verdict {PROMOTE, STAY, DEMOTE}

    /**
     * @param ovrDiff     OVR − 레벨 평균
     * @param performance 성적 점수 (±4)
     * @param score       판정 점수
     */
    public record Decision(Verdict verdict, LeagueLevel from, LeagueLevel to, double ovrDiff, double performance,
                           double score) {
        public boolean moved() {
            return verdict != Verdict.STAY;
        }
    }

    /** 9의 배수 시리즈를 마친 뒤, 시즌 마지막(54) 전까지만 판정 */
    public static boolean isEvaluationPoint(int seriesPlayed, int seriesPerSeason) {
        return isEvaluationPoint(MLB, seriesPlayed, seriesPerSeason);
    }

    /** 판정 주기의 배수 시리즈를 마친 뒤, 시즌 마지막 전까지만 판정 */
    public static boolean isEvaluationPoint(Params params, int seriesPlayed, int seriesPerSeason) {
        return seriesPlayed % params.interval() == 0 && seriesPlayed < seriesPerSeason;
    }

    /** MLB 레벨 순서로 판정 */
    public static Decision evaluate(LeagueLevel level, double overall, Position position,
                                    BattingLine recentBatting, PitchingLine recentPitching) {
        LeagueLevel[] levels = LeagueLevel.of(level.getMode()).toArray(LeagueLevel[]::new);
        int index = level.ordinal() - levels[0].ordinal();
        LeagueLevel above = index + 1 < levels.length ? levels[index + 1] : null;
        LeagueLevel below = index > 0 ? levels[index - 1] : null;
        return evaluate(MLB, level, above, below, overall, position, recentBatting, recentPitching);
    }

    /**
     * @param above 한 단계 위 레벨 (최상위면 null — 승격 없음)
     * @param below 한 단계 아래 레벨 (최하위면 null — 강등 없음)
     */
    public static Decision evaluate(Params params, LeagueLevel level, LeagueLevel above, LeagueLevel below,
                                    double overall, Position position,
                                    BattingLine recentBatting, PitchingLine recentPitching) {
        double ovrDiff = overall - level.getAverageRating();
        double performance = performance(params, position, recentBatting, recentPitching);
        double score = ovrDiff + params.performanceWeight() * performance;

        if (score >= params.promoteAt() && above != null) {
            return new Decision(Verdict.PROMOTE, level, above, ovrDiff, performance, score);
        }
        if (score <= params.demoteAt() && below != null) {
            return new Decision(Verdict.DEMOTE, level, below, ovrDiff, performance, score);
        }
        return new Decision(Verdict.STAY, level, level, ovrDiff, performance, score);
    }

    static double performance(Position position, BattingLine batting, PitchingLine pitching) {
        return performance(MLB, position, batting, pitching);
    }

    static double performance(Params params, Position position, BattingLine batting, PitchingLine pitching) {
        double raw;
        if (!position.isPitcher()) {
            if (batting == null || batting.plateAppearances < params.minPlateAppearances()) {
                return 0;
            }
            raw = (batting.getOps() - params.baseOps()) / params.opsPerPoint();
        } else if (position == Position.SP) {
            if (pitching == null || pitching.outs < params.starterMinOuts()) {
                return 0;
            }
            raw = (params.starterBaseEra() - pitching.getEra()) / params.starterEraPerPoint();
        } else {
            if (pitching == null || pitching.outs < params.closerMinOuts()) {
                return 0;
            }
            raw = (params.closerBaseEra() - pitching.getEra()) / params.closerEraPerPoint();
        }
        return Math.max(-params.performanceCap(), Math.min(params.performanceCap(), raw));
    }
}
