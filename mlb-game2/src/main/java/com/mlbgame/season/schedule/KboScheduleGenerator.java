package com.mlbgame.season.schedule;

import com.mlbgame.league.Team;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * KBO 정규시즌 일정: 54시리즈 = 3연전 36 + 2연전 18 = 144경기 (DB 저장 없음 — 같은 시드면 같은 일정).
 *
 * <pre>
 * 상대 9팀 × (3연전 4 + 2연전 2) = 팀당 16경기, 홈·원정 8:8
 * 1~36번 시리즈는 3연전, 37~54번(8~9월)은 2연전 — 리그 전체가 같은 편성 ({@link #gamesInSeries})
 * 같은 상대 연속 시리즈는 피한다
 * </pre>
 * MLB 와 시리즈 수(54)가 같아 훈련 횟수·월간 시상 주기·연봉 적립이 그대로다 (docs/kbo-mode.md 3.2).
 */
public final class KboScheduleGenerator {

    public static final int SERIES_PER_SEASON = 54;
    public static final int THREE_GAME_SERIES = 36;
    static final int THREE_GAME_MEETINGS = 4;
    static final int TWO_GAME_MEETINGS = 2;

    private KboScheduleGenerator() {
    }

    /** 리그 전체 편성: 1~36번 3연전, 37~54번 2연전 */
    public static int gamesInSeries(int seriesNo) {
        return seriesNo <= THREE_GAME_SERIES ? 3 : 2;
    }

    public static List<ScheduledSeries> generate(long seed, Team myTeam, List<Team> allTeams) {
        RandomGenerator random = new SplittableRandom(seed);
        List<Long> opponents = allTeams.stream()
                .map(Team::getId)
                .filter(id -> !Objects.equals(id, myTeam.getId()))
                .toList();

        List<Long> threeGame = new ArrayList<>();
        List<Long> twoGame = new ArrayList<>();
        for (Long id : opponents) {
            for (int i = 0; i < THREE_GAME_MEETINGS; i++) threeGame.add(id);
            for (int i = 0; i < TWO_GAME_MEETINGS; i++) twoGame.add(id);
        }
        shuffle(threeGame, random);
        shuffle(twoGame, random);
        ScheduleGenerator.avoidBackToBack(threeGame);
        ScheduleGenerator.avoidBackToBack(twoGame);

        // 상대마다 홈·원정을 번갈아 (3연전 2·2, 2연전 1·1 → 8경기씩). 첫 시리즈 홈 여부는 상대마다 무작위
        Map<Long, Boolean> startsHome = new HashMap<>();
        opponents.forEach(id -> startsHome.put(id, random.nextBoolean()));
        Map<Long, Integer> threeCount = new HashMap<>();
        Map<Long, Integer> twoCount = new HashMap<>();

        List<ScheduledSeries> schedule = new ArrayList<>();
        int seriesNo = 1;
        for (Long id : threeGame) {
            int k = threeCount.merge(id, 1, Integer::sum) - 1;
            schedule.add(new ScheduledSeries(seriesNo++, id, (k % 2 == 0) == startsHome.get(id), 3));
        }
        for (Long id : twoGame) {
            int k = twoCount.merge(id, 1, Integer::sum) - 1;
            schedule.add(new ScheduledSeries(seriesNo++, id, (k % 2 == 0) != startsHome.get(id), 2));
        }
        return List.copyOf(schedule);
    }

    private static <T> void shuffle(List<T> list, RandomGenerator random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
