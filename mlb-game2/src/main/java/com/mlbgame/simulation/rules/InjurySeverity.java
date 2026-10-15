package com.mlbgame.simulation.rules;

import java.util.random.RandomGenerator;

/** 부상 심각도와 결장 기간(시리즈 단위) */
public enum InjurySeverity {
    MINOR("경미", 1, 3, 65),
    MAJOR("중상", 5, 20, 30),
    SEASON_ENDING("시즌 아웃", Integer.MAX_VALUE, Integer.MAX_VALUE, 5);

    private final String label;
    private final int minSeries;
    private final int maxSeries;
    private final int weight;

    InjurySeverity(String label, int minSeries, int maxSeries, int weight) {
        this.label = label;
        this.minSeries = minSeries;
        this.maxSeries = maxSeries;
        this.weight = weight;
    }

    public static InjurySeverity roll(RandomGenerator random) {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (InjurySeverity severity : values()) {
            cumulative += severity.weight;
            if (roll < cumulative) {
                return severity;
            }
        }
        return MINOR;
    }

    /** 결장 시리즈 수. 시즌 아웃은 {@link Integer#MAX_VALUE} (남은 시즌 전체) */
    public int rollSeriesOut(RandomGenerator random) {
        if (this == SEASON_ENDING) {
            return Integer.MAX_VALUE;
        }
        return minSeries + random.nextInt(maxSeries - minSeries + 1);
    }

    public String getLabel() {
        return label;
    }
}
