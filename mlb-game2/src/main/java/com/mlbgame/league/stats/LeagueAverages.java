package com.mlbgame.league.stats;

import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;

/**
 * 한 시즌 메이저리그 기록 합계 → 리그 평균 (wOBA, 타석당 득점, 출루율, 장타율, 평균 실점(RA9), FIP 상수).
 * 투수 기록에는 자책점 구분이 없어 ERA 는 실점 기준이다.
 */
public record LeagueAverages(
        long plateAppearances, long atBats, long hits, long doubles, long triples, long homeRuns, long walks,
        long hitByPitch, long sacFlies, long runs,
        long pitchingOuts, long pitchingRuns, long pitchingHomeRuns, long pitchingWalks, long pitchingStrikeouts
) {

    /**
     * 리그 평균을 저장하지 못한 시즌(이번 패치 이전에 끝난 시즌, 아직 한 달도 안 지난 시즌)에 쓰는 기준값.
     * 타석 1,000개 기준, 시뮬레이션 리그 평균 설정(application.yml simulation.baseline — MLB 2023~24 근사)에서 계산:
     * 볼넷 8.5% · 사구 1.1% · 삼진 22.5% · 홈런 3.1% · BABIP .295 · 인플레이 안타 중 2루타 23%·3루타 2% · 타석당 득점 .118
     */
    public static final LeagueAverages BASELINE = new LeagueAverages(
            1000, 904, 222, 44, 4, 31, 85, 11, 0, 118,
            680, 118, 31, 85, 225);

    /** wOBA 가중치 (MLB 최근 시즌 근사, 고정) */
    static final double W_BB = 0.69;
    static final double W_HBP = 0.72;
    static final double W_1B = 0.88;
    static final double W_2B = 1.25;
    static final double W_3B = 1.58;
    static final double W_HR = 2.03;

    public static LeagueAverages of(BattingLine b, PitchingLine p) {
        return new LeagueAverages(b.plateAppearances, b.atBats, b.hits, b.doubles, b.triples, b.homeRuns, b.walks,
                b.hitByPitch, b.sacFlies, b.runs, p.outs, p.runs, p.homeRuns, p.walks, p.strikeouts);
    }

    public static double woba(long atBats, long hits, long doubles, long triples, long homeRuns, long walks,
                              long hitByPitch, long sacFlies) {
        long denominator = atBats + walks + sacFlies + hitByPitch;
        if (denominator == 0) {
            return 0;
        }
        long singles = hits - doubles - triples - homeRuns;
        return (W_BB * walks + W_HBP * hitByPitch + W_1B * singles + W_2B * doubles + W_3B * triples + W_HR * homeRuns)
                / denominator;
    }

    public double woba() {
        return woba(atBats, hits, doubles, triples, homeRuns, walks, hitByPitch, sacFlies);
    }

    public double runsPerPa() {
        return plateAppearances == 0 ? 0 : (double) runs / plateAppearances;
    }

    public double obp() {
        long denominator = atBats + walks + hitByPitch + sacFlies;
        return denominator == 0 ? 0 : (double) (hits + walks + hitByPitch) / denominator;
    }

    public double slg() {
        if (atBats == 0) {
            return 0;
        }
        long totalBases = hits + doubles + 2 * triples + 3 * homeRuns;
        return (double) totalBases / atBats;
    }

    /** 리그 평균 실점 (9이닝당) — ERA+·FIP− 의 기준이자 리그 FIP */
    public double era() {
        return pitchingOuts == 0 ? 0 : pitchingRuns * 27.0 / pitchingOuts;
    }

    /** FIP 상수: 리그 FIP = 리그 ERA 가 되게 맞춘다 */
    public double fipConstant() {
        if (pitchingOuts == 0) {
            return 0;
        }
        double innings = pitchingOuts / 3.0;
        return era() - (13.0 * pitchingHomeRuns + 3.0 * pitchingWalks - 2.0 * pitchingStrikeouts) / innings;
    }

    /** 계산에 쓸 만큼 기록이 있는가 (없으면 기준값을 쓴다) */
    public boolean usable() {
        return plateAppearances > 0 && pitchingOuts > 0 && runs > 0;
    }
}
