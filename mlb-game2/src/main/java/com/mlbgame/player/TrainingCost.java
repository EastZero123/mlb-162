package com.mlbgame.player;

/**
 * 훈련 비용: 능력치 +1 에 필요한 훈련 횟수 = 구간 비용 × 나이 비용. 랜덤 없음.
 *
 * <pre>
 * 능력치 구간   ~84: 1   85~89: 2   90~94: 4   95~99: 8
 * 나이         ~29: 1   30~32: 2   33~35: 4   36~: 8
 * </pre>
 * 예) 30세 · 능력치 88 → 2 × 2 = 4회 훈련마다 +1. 스탯업 포인트는 비용 없이 바로 +1.
 */
public final class TrainingCost {

    private TrainingCost() {
    }

    public static int tier(int value) {
        if (value < 85) return 1;
        if (value < 90) return 2;
        if (value < 95) return 4;
        return 8;
    }

    public static int ageFactor(int age) {
        if (age <= 29) return 1;
        if (age <= 32) return 2;
        if (age <= 35) return 4;
        return 8;
    }

    public static int of(int value, int age) {
        return tier(value) * ageFactor(age);
    }
}
