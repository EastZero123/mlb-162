package com.mlbgame.simulation;

import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 한 타석의 결과별 확률 (합계 1).
 */
public record PaProbabilities(
        double strikeout,
        double walk,
        double hitByPitch,
        double single,
        double doubles,
        double triple,
        double homeRun,
        double groundOut,
        double flyOut
) {

    public Map<PaResult, Double> asMap() {
        Map<PaResult, Double> map = new EnumMap<>(PaResult.class);
        map.put(PaResult.STRIKEOUT, strikeout);
        map.put(PaResult.WALK, walk);
        map.put(PaResult.HIT_BY_PITCH, hitByPitch);
        map.put(PaResult.SINGLE, single);
        map.put(PaResult.DOUBLE, doubles);
        map.put(PaResult.TRIPLE, triple);
        map.put(PaResult.HOME_RUN, homeRun);
        map.put(PaResult.GROUND_OUT, groundOut);
        map.put(PaResult.FLY_OUT, flyOut);
        return map;
    }

    public double total() {
        return strikeout + walk + hitByPitch + single + doubles + triple + homeRun + groundOut + flyOut;
    }

    /** 시드 기반 난수로 결과 하나를 뽑는다 (같은 난수열이면 같은 결과) */
    public PaResult draw(RandomGenerator random) {
        double roll = random.nextDouble() * total();
        double cumulative = 0;
        for (Map.Entry<PaResult, Double> entry : asMap().entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return PaResult.FLY_OUT;
    }

    // ---- 기대 기록 (검증·밸런스 확인용) ----

    public double hits() {
        return single + doubles + triple + homeRun;
    }

    /** 기대 타율 (희생타 무시) */
    public double expectedAverage() {
        return hits() / (1 - walk - hitByPitch);
    }

    /** 기대 출루율 */
    public double expectedObp() {
        return hits() + walk + hitByPitch;
    }

    /** 기대 장타율 */
    public double expectedSlg() {
        return (single + 2 * doubles + 3 * triple + 4 * homeRun) / (1 - walk - hitByPitch);
    }
}
