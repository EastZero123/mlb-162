package com.mlbgame.simulation.rules;

/**
 * 피로도 0~100. 경기 출전으로만 쌓이고(훈련과 무관), 시리즈 사이 휴식일에 회복된다.
 * 70을 넘으면 경기력 저하(능력치 −5)와 부상 위험 증가.
 */
public final class Fatigue {

    public static final int MAX = 100;
    public static final int TIRED_THRESHOLD = 70;
    public static final double TIRED_PENALTY = -5;

    /** 경기별 피로 증가량 */
    public static final int HITTER_START = 4;
    public static final int HITTER_PINCH = 1;
    public static final int STARTER_PER_10_PITCHES = 3;   // 90구 → +27
    public static final int CLOSER_APPEARANCE = 8;

    /** 휴식 회복: 출전하지 않은 경기 1회당 / 시리즈 사이 이동일 */
    public static final int RECOVER_PER_DAY_OFF = 10;
    public static final int RECOVER_BETWEEN_SERIES = 8;

    private Fatigue() {
    }

    /** 내구성이 높을수록 덜 쌓인다 (내구성 65 기준 ×1.0, 10당 ±8%) */
    public static int gain(int base, int durability) {
        double factor = 1 - 0.08 * (durability - 65) / 10.0;
        return (int) Math.round(base * Math.max(0.5, Math.min(1.5, factor)));
    }

    /** 내구성이 높을수록 빨리 회복 */
    public static int recover(int fatigue, int amount, int durability) {
        double factor = 1 + 0.08 * (durability - 65) / 10.0;
        return clamp(fatigue - (int) Math.round(amount * Math.max(0.5, Math.min(1.5, factor))));
    }

    public static int add(int fatigue, int gain) {
        return clamp(fatigue + gain);
    }

    public static double penalty(int fatigue) {
        return fatigue > TIRED_THRESHOLD ? TIRED_PENALTY : 0;
    }

    /** 컨디션 + 피로를 합친 경기력 보정 (능력치 단위) */
    public static double formOffset(Condition condition, int fatigue) {
        return condition.getOffset() + penalty(fatigue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX, value));
    }
}
