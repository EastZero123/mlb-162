package com.mlbgame.award;

import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.Collection;

/**
 * 시상 판정 점수 (리그 평균 대비 아낀·만든 실점에 가까운 단순 지표). 점수가 비교 대상 전원보다 높으면 수상.
 *
 * <pre>
 * 타자   = 타석 × (OPS − .720) / 2.2 + 수비로 막은 실점 + 0.2 × 도루
 * 선발   = (4.40 − ERA) × 이닝/9 + 0.03 × 탈삼진 + 0.3 × 승
 * 구원   = (4.00 − ERA) × 이닝/9 + 0.3 × 세이브 − 0.5 × 블론세이브
 * </pre>
 */
public final class AwardRules {

    // 월간 최소 표본
    public static final int MONTH_MIN_PA = 60;
    public static final int MONTH_MIN_SP_OUTS = 60;      // 20이닝
    public static final int MONTH_MIN_RP_OUTS = 15;      // 5이닝
    // 시즌 최소 표본
    public static final int SEASON_MIN_PA = 400;
    public static final int SEASON_MIN_SP_OUTS = 360;    // 120이닝
    public static final int SEASON_MIN_RP_OUTS = 120;    // 40이닝
    public static final int ROOKIE_MIN_PA = 250;
    public static final int ROOKIE_MIN_SP_OUTS = 180;    // 60이닝
    public static final int GOLD_GLOVE_MIN_GAMES = 100;
    // 올스타 (전반기 = 1~3개월)
    public static final int ALL_STAR_MIN_PA = 120;
    public static final int ALL_STAR_MIN_SP_OUTS = 120;
    public static final int ALL_STAR_MIN_RP_OUTS = 36;
    public static final int ALL_STAR_HITTERS_PER_POSITION = 2;
    public static final int ALL_STAR_STARTERS = 6;
    public static final int ALL_STAR_RELIEVERS = 4;
    /** 올스타전 MVP 확률 (선정된 경우) */
    public static final double ALL_STAR_MVP_CHANCE = 0.08;
    /** 마이너리그 올스타: 비교군 상위 몇 명 안에 들면 */
    public static final int MINOR_ALL_STAR_RANK = 3;

    private AwardRules() {
    }

    public static double hitter(BattingLine b, FieldingLine f) {
        if (b == null || b.plateAppearances == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return b.plateAppearances * (b.getOps() - 0.720) / 2.2 + (f == null ? 0 : f.getRunsSaved()) + 0.2 * b.stolenBases;
    }

    public static double starter(PitchingLine p) {
        if (p == null || p.outs == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return (4.40 - p.getEra()) * p.outs / 27.0 + 0.03 * p.strikeouts + 0.3 * p.wins;
    }

    public static double reliever(PitchingLine p) {
        if (p == null || p.outs == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return (4.00 - p.getEra()) * p.outs / 27.0 + 0.3 * p.saves - 0.5 * p.blownSaves;
    }

    /** 내 점수가 비교 대상 전원보다 높은가 (비교 대상이 없으면 수상) */
    public static boolean beatsAll(double mine, Collection<Double> others) {
        return mine > Double.NEGATIVE_INFINITY && others.stream().allMatch(o -> mine > o);
    }

    /** 내 순위 (1부터) */
    public static int rank(double mine, Collection<Double> others) {
        return 1 + (int) others.stream().filter(o -> o >= mine).count();
    }
}
