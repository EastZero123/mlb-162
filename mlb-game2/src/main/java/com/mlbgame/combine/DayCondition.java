package com.mlbgame.combine;

import com.mlbgame.common.Dice;
import com.mlbgame.common.Messages;

/**
 * 컴바인 당일 컨디션. 모든 종목 점수에 같은 편차가 더해진다.
 * 확률: 최악 10% / 나쁨 20% / 보통 40% / 좋음 20% / 최상 10%
 */
public enum DayCondition {
    WORST("최악", -8, 10),
    BAD("나쁨", -4, 20),
    NORMAL("보통", 0, 40),
    GOOD("좋음", 4, 20),
    BEST("최상", 8, 10);

    private final String label;
    private final int offset;
    private final int weight;

    DayCondition(String label, int offset, int weight) {
        this.label = label;
        this.offset = offset;
        this.weight = weight;
    }

    public static DayCondition roll(Dice dice) {
        int roll = dice.nextInt(100);
        int cumulative = 0;
        for (DayCondition condition : values()) {
            cumulative += condition.weight;
            if (roll < cumulative) {
                return condition;
            }
        }
        return NORMAL;
    }

    public String getLabel() {
        return Messages.text("DayCondition." + name(), label);
    }

    public int getOffset() {
        return offset;
    }
}
