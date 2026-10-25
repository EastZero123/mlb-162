package com.mlbgame.season.schedule;

/**
 * 시즌 일정의 시리즈 하나.
 *
 * @param seriesNo   1~54
 * @param opponentId 상대 구단 id (마이너리그면 그 구단의 같은 레벨 산하팀)
 * @param home       홈 시리즈 여부
 * @param games      경기 수 (MLB 3연전, KBO 는 3연전·2연전)
 */
public record ScheduledSeries(int seriesNo, Long opponentId, boolean home, int games) {

    public static final int SERIES_PER_MONTH = 9;
    public static final int DEFAULT_GAMES = 3;

    public ScheduledSeries(int seriesNo, Long opponentId, boolean home) {
        this(seriesNo, opponentId, home, DEFAULT_GAMES);
    }

    /** 4월(4) ~ 9월(9). 9시리즈마다 한 달 — 월간 시상(M4) 구간 */
    public int month() {
        return 4 + (seriesNo - 1) / SERIES_PER_MONTH;
    }
}
