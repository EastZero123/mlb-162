package com.mlbgame.league.stats;

import com.mlbgame.player.Position;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;

/**
 * 세부 지표 계산 (순수 계산). 리그 평균이 필요 없는 지표는 늘 계산하고,
 * 리그 평균이 필요한 지표(wOBA 제외 — wOBA 는 가중치가 고정이라 늘 계산)는 {@code league} 가 없으면 null.
 *
 * <pre>
 * 타자
 *   wOBA     = (0.69 BB + 0.72 HBP + 0.88 1B + 1.25 2B + 1.58 3B + 2.03 HR) / (AB + BB + SF + HBP)
 *   타격 득점  = (wOBA − 리그 wOBA) / 1.2 × PA
 *   wRC+     = 100 + 100 × (타격 득점 / PA) / 리그 타석당 득점          (구장 보정 없음)
 *   OPS+     = 100 × (OBP / 리그 OBP + SLG / 리그 SLG − 1)
 *   WAR      = (타격 득점 + 주루(SB 0.2 − CS 0.4) + 수비(막은 실점) + 포지션 보정 + 대체 선수 600타석당 20) / 10
 *   포지션 보정 (162경기당)  C +12.5 · SS +7.5 · 2B/3B +2.5 · OF −4 · 1B −12.5
 * 투수 (자책점 구분이 없어 ERA 는 실점 기준, 투수 사구 기록이 없어 FIP 에서 빠진다)
 *   FIP      = (13 HR + 3 BB − 2 K) / IP + 리그 FIP 상수
 *   ERA+     = 100 × 리그 ERA / ERA            FIP− = 100 × FIP / 리그 ERA (낮을수록 좋음)
 *   WAR      = (리그 ERA + 대체 선수 차이 − FIP) × IP / 9 / 10   대체 선수 차이: 선발 1.0 · 불펜 0.6
 * </pre>
 */
public final class AdvancedStats {

    static final double WOBA_SCALE = 1.2;
    static final double RUNS_PER_WIN = 10;
    static final double REPLACEMENT_RUNS_PER_600_PA = 20;
    static final double STOLEN_BASE_RUNS = 0.2;
    static final double CAUGHT_STEALING_RUNS = -0.4;
    static final double SEASON_GAMES = 162;
    static final double STARTER_REPLACEMENT_GAP = 1.0;
    static final double RELIEVER_REPLACEMENT_GAP = 0.6;

    private AdvancedStats() {
    }

    /**
     * @param iso           순장타율 (SLG − AVG)
     * @param babip         인플레이 타구 타율
     * @param walkRate      BB%
     * @param strikeoutRate K%
     * @param woba          wOBA
     * @param wrcPlus       wRC+ (리그 평균 없으면 null)
     * @param opsPlus       OPS+ (리그 평균 없으면 null)
     * @param war           WAR (리그 평균 없으면 null)
     */
    public record Hitter(double iso, double babip, double walkRate, double strikeoutRate, double woba,
                         Double wrcPlus, Double opsPlus, Double war) {
    }

    /**
     * @param kMinusBb K% − BB%
     * @param fip      FIP (리그 평균 없으면 null)
     * @param eraPlus  ERA+ (리그 평균 없거나 실점 0이면 null)
     * @param fipMinus FIP− (리그 평균 없으면 null)
     * @param war      WAR (리그 평균 없으면 null)
     */
    public record Pitcher(double strikeoutRate, double walkRate, double kMinusBb, Double fip, Double eraPlus,
                          Double fipMinus, Double war) {
    }

    public static Hitter hitter(BattingLine b, FieldingLine f, Position position, LeagueAverages league) {
        return hitter(b, f, position, league, SEASON_GAMES);
    }

    /** @param seasonGames 포지션 보정 기준 시즌 경기 수 (모드별: MLB 162) */
    public static Hitter hitter(BattingLine b, FieldingLine f, Position position, LeagueAverages league,
                                double seasonGames) {
        int pa = b.plateAppearances;
        double iso = b.getSlg() - b.getAverage();
        int ballsInPlay = b.atBats - b.strikeouts - b.homeRuns + b.sacFlies;
        double babip = ballsInPlay <= 0 ? 0 : (double) (b.hits - b.homeRuns) / ballsInPlay;
        double walkRate = pa == 0 ? 0 : (double) b.walks / pa;
        double strikeoutRate = pa == 0 ? 0 : (double) b.strikeouts / pa;
        double woba = LeagueAverages.woba(b.atBats, b.hits, b.doubles, b.triples, b.homeRuns, b.walks, b.hitByPitch,
                b.sacFlies);
        if (league == null || pa == 0) {
            return new Hitter(iso, babip, walkRate, strikeoutRate, woba, null, null, null);
        }

        double battingRuns = (woba - league.woba()) / WOBA_SCALE * pa;
        double wrcPlus = 100 + 100 * (battingRuns / pa) / league.runsPerPa();
        double opsPlus = 100 * (b.getObp() / league.obp() + b.getSlg() / league.slg() - 1);
        double baserunning = b.stolenBases * STOLEN_BASE_RUNS + b.caughtStealing * CAUGHT_STEALING_RUNS;
        double fielding = f == null ? 0 : f.getRunsSaved();
        double positional = positionalAdjustment(position) * b.games / seasonGames;
        double replacement = REPLACEMENT_RUNS_PER_600_PA * pa / 600.0;
        double war = (battingRuns + baserunning + fielding + positional + replacement) / RUNS_PER_WIN;
        return new Hitter(iso, babip, walkRate, strikeoutRate, woba, wrcPlus, opsPlus, war);
    }

    public static Pitcher pitcher(PitchingLine p, LeagueAverages league) {
        int bf = p.battersFaced;
        double strikeoutRate = bf == 0 ? 0 : (double) p.strikeouts / bf;
        double walkRate = bf == 0 ? 0 : (double) p.walks / bf;
        if (league == null || p.outs == 0) {
            return new Pitcher(strikeoutRate, walkRate, strikeoutRate - walkRate, null, null, null, null);
        }

        double innings = p.outs / 3.0;
        double fip = (13.0 * p.homeRuns + 3.0 * p.walks - 2.0 * p.strikeouts) / innings + league.fipConstant();
        double era = p.getEra();
        Double eraPlus = era == 0 ? null : 100 * league.era() / era;
        double fipMinus = 100 * fip / league.era();
        boolean starter = p.gamesStarted * 2 >= p.games;
        double gap = starter ? STARTER_REPLACEMENT_GAP : RELIEVER_REPLACEMENT_GAP;
        double war = (league.era() + gap - fip) * innings / 9 / RUNS_PER_WIN;
        return new Pitcher(strikeoutRate, walkRate, strikeoutRate - walkRate, fip, eraPlus, fipMinus, war);
    }

    /** 포지션 보정 득점 (162경기 기준) */
    static double positionalAdjustment(Position position) {
        return switch (position) {
            case C -> 12.5;
            case SS -> 7.5;
            case B2, B3 -> 2.5;
            case OF -> -4.0;
            case B1 -> -12.5;
            case SP, CP, RP -> 0;
        };
    }
}
