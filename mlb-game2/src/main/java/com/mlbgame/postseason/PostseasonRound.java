package com.mlbgame.postseason;

import com.mlbgame.common.Messages;

/**
 * 포스트시즌 라운드 (MLB 2022~ 방식). 홈 경기 배정: 3전제는 모두 상위 시드 홈, 5전제 2-2-1, 7전제 2-3-2.
 */
public enum PostseasonRound {
    WILD_CARD(1, "와일드카드 시리즈", 3, "HHH"),
    DIVISION(2, "디비전시리즈", 5, "HHAAH"),
    CHAMPIONSHIP(3, "리그 챔피언십시리즈", 7, "HHAAAHH"),
    WORLD_SERIES(4, "월드시리즈", 7, "HHAAAHH");

    private final int number;
    private final String label;
    private final int maxGames;
    private final String homePattern;

    PostseasonRound(int number, String label, int maxGames, String homePattern) {
        this.number = number;
        this.label = label;
        this.maxGames = maxGames;
        this.homePattern = homePattern;
    }

    public static PostseasonRound of(int number) {
        for (PostseasonRound round : values()) {
            if (round.number == number) return round;
        }
        throw new IllegalArgumentException("라운드 번호: " + number);
    }

    public int winsNeeded() {
        return maxGames / 2 + 1;
    }

    /** gameIndex(0부터) 경기가 상위 시드 홈인지 */
    public boolean highSeedHome(int gameIndex) {
        return homePattern.charAt(gameIndex) == 'H';
    }

    public int getNumber() {
        return number;
    }

    public String getLabel() {
        return Messages.text("PostseasonRound." + name(), label);
    }

    public int getMaxGames() {
        return maxGames;
    }
}
