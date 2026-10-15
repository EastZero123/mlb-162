package com.mlbgame.simulation.game;

/** 투구 기록 (경기·시리즈·시즌 누적에 공용). 자책점은 실점과 같게 단순화 */
public class PitchingLine {

    public int games;
    public int gamesStarted;
    public int outs;
    public int battersFaced;
    public int pitches;
    public int hits;
    public int runs;
    public int walks;
    public int strikeouts;
    public int homeRuns;
    public int wins;
    public int losses;
    public int saves;
    public int blownSaves;
    public int qualityStarts;

    public void add(PitchingLine o) {
        games += o.games;
        gamesStarted += o.gamesStarted;
        outs += o.outs;
        battersFaced += o.battersFaced;
        pitches += o.pitches;
        hits += o.hits;
        runs += o.runs;
        walks += o.walks;
        strikeouts += o.strikeouts;
        homeRuns += o.homeRuns;
        wins += o.wins;
        losses += o.losses;
        saves += o.saves;
        blownSaves += o.blownSaves;
        qualityStarts += o.qualityStarts;
    }

    /** "6.1" 형식 이닝 표기 */
    public String getInnings() {
        return (outs / 3) + "." + (outs % 3);
    }

    public double getEra() {
        return outs == 0 ? 0 : runs * 27.0 / outs;
    }

    public double getWhip() {
        return outs == 0 ? 0 : (walks + hits) * 3.0 / outs;
    }

    public double getStrikeoutsPerNine() {
        return outs == 0 ? 0 : strikeouts * 27.0 / outs;
    }

    public double getWalksPerNine() {
        return outs == 0 ? 0 : walks * 27.0 / outs;
    }

    public double getHomeRunsPerNine() {
        return outs == 0 ? 0 : homeRuns * 27.0 / outs;
    }

    /** "6이닝 2실점 8탈삼진" */
    public String summary() {
        String innings = outs % 3 == 0 ? String.valueOf(outs / 3) : (outs / 3) + " " + (outs % 3) + "/3";
        return innings + "이닝 " + runs + "실점 " + strikeouts + "탈삼진";
    }
}
