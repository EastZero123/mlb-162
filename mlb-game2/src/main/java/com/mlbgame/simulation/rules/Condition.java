package com.mlbgame.simulation.rules;

import com.mlbgame.common.Messages;
import java.util.random.RandomGenerator;

/**
 * 시리즈마다 바뀌는 컨디션 5단계. 경기력 ±5% 수준을 능력치 오프셋으로 반영한다.
 * 확률: 최악 10% / 나쁨 20% / 보통 40% / 좋음 20% / 최상 10%
 */
public enum Condition {
    WORST("최악", -4, 10),
    BAD("나쁨", -2, 20),
    NORMAL("보통", 0, 40),
    GOOD("좋음", 2, 20),
    BEST("최상", 4, 10);

    private final String label;
    private final int offset;
    private final int weight;

    Condition(String label, int offset, int weight) {
        this.label = label;
        this.offset = offset;
        this.weight = weight;
    }

    public static Condition roll(RandomGenerator random) {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (Condition condition : values()) {
            cumulative += condition.weight;
            if (roll < cumulative) {
                return condition;
            }
        }
        return NORMAL;
    }

    public String getLabel() {
        return Messages.text("Condition." + name(), label);
    }

    public int getOffset() {
        return offset;
    }
}
