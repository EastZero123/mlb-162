package com.mlbgame.simulation.game;

import com.mlbgame.common.Messages;

/** 타격 기록 (경기·시리즈·시즌 누적에 공용) */
public class BattingLine {

    public int games;
    public int plateAppearances;
    public int atBats;
    public int hits;
    public int doubles;
    public int triples;
    public int homeRuns;
    public int runs;
    public int rbi;
    public int walks;
    public int hitByPitch;
    public int strikeouts;
    public int stolenBases;
    public int caughtStealing;
    public int sacFlies;
    public int groundIntoDoublePlay;
    public int rispAtBats;
    public int rispHits;

    public void add(BattingLine o) {
        games += o.games;
        plateAppearances += o.plateAppearances;
        atBats += o.atBats;
        hits += o.hits;
        doubles += o.doubles;
        triples += o.triples;
        homeRuns += o.homeRuns;
        runs += o.runs;
        rbi += o.rbi;
        walks += o.walks;
        hitByPitch += o.hitByPitch;
        strikeouts += o.strikeouts;
        stolenBases += o.stolenBases;
        caughtStealing += o.caughtStealing;
        sacFlies += o.sacFlies;
        groundIntoDoublePlay += o.groundIntoDoublePlay;
        rispAtBats += o.rispAtBats;
        rispHits += o.rispHits;
    }

    public double getAverage() {
        return atBats == 0 ? 0 : (double) hits / atBats;
    }

    public double getObp() {
        int denominator = atBats + walks + hitByPitch + sacFlies;
        return denominator == 0 ? 0 : (double) (hits + walks + hitByPitch) / denominator;
    }

    public double getSlg() {
        int singles = hits - doubles - triples - homeRuns;
        return atBats == 0 ? 0 : (double) (singles + 2 * doubles + 3 * triples + 4 * homeRuns) / atBats;
    }

    public double getOps() {
        return getObp() + getSlg();
    }

    public double getRispAverage() {
        return rispAtBats == 0 ? 0 : (double) rispHits / rispAtBats;
    }

    /** "4타수 2안타 1홈런 3타점" */
    public String summary() {
        StringBuilder sb = new StringBuilder(Messages.get("line.bat.hits", hits, atBats));
        if (homeRuns > 0) sb.append(Messages.get("line.bat.hr", homeRuns));
        if (rbi > 0) sb.append(Messages.get("line.bat.rbi", rbi));
        if (walks > 0) sb.append(Messages.get("line.bat.bb", walks));
        if (stolenBases > 0) sb.append(Messages.get("line.bat.sb", stolenBases));
        return sb.toString();
    }
}
