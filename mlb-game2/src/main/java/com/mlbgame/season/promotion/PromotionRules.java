package com.mlbgame.season.promotion;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;

/**
 * 승격·강등 판정 (9시리즈마다). 능력치와 최근 성적을 섞는다.
 *
 * <pre>
 * 판정 점수 = (OVR − 소속 레벨 평균) + 2 × 성적 점수
 * 성적 점수 타자 (OPS − .720) / .050       (최소 40타석)
 *          선발 (4.30 − ERA) / 0.60        (최소 15이닝)
 *          마무리 (4.00 − ERA) / 0.80      (최소 5이닝)
 *          ±4 로 제한, 표본이 부족하면 0
 * 승격 +4 이상 / 강등 −8 이하, 한 번에 한 레벨
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

    private PromotionRules() {
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
        return seriesPlayed % EVALUATION_INTERVAL == 0 && seriesPlayed < seriesPerSeason;
    }

    public static Decision evaluate(LeagueLevel level, double overall, Position position,
                                    BattingLine recentBatting, PitchingLine recentPitching) {
        double ovrDiff = overall - level.getAverageRating();
        double performance = performance(position, recentBatting, recentPitching);
        double score = ovrDiff + PERFORMANCE_WEIGHT * performance;

        if (score >= PROMOTE_AT && level != LeagueLevel.MLB) {
            return new Decision(Verdict.PROMOTE, level, LeagueLevel.values()[level.ordinal() + 1], ovrDiff, performance, score);
        }
        if (score <= DEMOTE_AT && level != LeagueLevel.ROOKIE) {
            return new Decision(Verdict.DEMOTE, level, LeagueLevel.values()[level.ordinal() - 1], ovrDiff, performance, score);
        }
        return new Decision(Verdict.STAY, level, level, ovrDiff, performance, score);
    }

    static double performance(Position position, BattingLine batting, PitchingLine pitching) {
        double raw;
        if (!position.isPitcher()) {
            if (batting == null || batting.plateAppearances < MIN_PLATE_APPEARANCES) {
                return 0;
            }
            raw = (batting.getOps() - BASE_OPS) / OPS_PER_POINT;
        } else if (position == Position.SP) {
            if (pitching == null || pitching.outs < STARTER_MIN_OUTS) {
                return 0;
            }
            raw = (STARTER_BASE_ERA - pitching.getEra()) / STARTER_ERA_PER_POINT;
        } else {
            if (pitching == null || pitching.outs < CLOSER_MIN_OUTS) {
                return 0;
            }
            raw = (CLOSER_BASE_ERA - pitching.getEra()) / CLOSER_ERA_PER_POINT;
        }
        return Math.max(-PERFORMANCE_CAP, Math.min(PERFORMANCE_CAP, raw));
    }
}
