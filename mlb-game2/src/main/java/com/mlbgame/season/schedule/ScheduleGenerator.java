package com.mlbgame.season.schedule;

import com.mlbgame.league.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * 시즌 54시리즈 일정 생성 (DB 저장 없음 — 같은 시드면 같은 일정, DB + 시드 재현 원칙).
 *
 * <pre>
 * 같은 지구 4팀 × 4시리즈 = 16
 * 같은 리그 다른 지구 10팀 × 2시리즈 = 20
 * 인터리그 15팀 × 1시리즈 + 그중 3팀 한 번 더 = 18
 * 합계 54 (홈 27 / 원정 27), 같은 상대 연속 시리즈는 피한다
 * </pre>
 * 마이너리그도 같은 구조로 만들고, 상대는 각 구단의 같은 레벨 산하팀으로 본다.
 */
public final class ScheduleGenerator {

    public static final int SERIES_PER_SEASON = 54;
    static final int DIVISION_MEETINGS = 4;
    static final int LEAGUE_MEETINGS = 2;
    static final int EXTRA_INTERLEAGUE = 3;

    private ScheduleGenerator() {
    }

    /** 일정 시드: 월드 시드 + 시즌 연도 + 구단 */
    public static long seed(long worldSeed, int year, Long teamId) {
        return worldSeed * 31 + year * 1_000_003L + teamId;
    }

    public static List<ScheduledSeries> generate(long seed, Team myTeam, List<Team> allTeams) {
        RandomGenerator random = new SplittableRandom(seed);
        List<Team> division = new ArrayList<>();
        List<Team> league = new ArrayList<>();
        List<Team> interleague = new ArrayList<>();
        for (Team team : allTeams) {
            if (Objects.equals(team.getId(), myTeam.getId())) {
                continue;
            }
            if (!team.getLeague().equals(myTeam.getLeague())) {
                interleague.add(team);
            } else if (team.getDivision().equals(myTeam.getDivision())) {
                division.add(team);
            } else {
                league.add(team);
            }
        }

        List<Long> opponents = new ArrayList<>();
        division.forEach(t -> repeat(opponents, t.getId(), DIVISION_MEETINGS));
        league.forEach(t -> repeat(opponents, t.getId(), LEAGUE_MEETINGS));
        interleague.forEach(t -> opponents.add(t.getId()));
        List<Team> extra = new ArrayList<>(interleague);
        shuffle(extra, random);
        for (int i = 0; i < EXTRA_INTERLEAGUE && i < extra.size(); i++) {
            opponents.add(extra.get(i).getId());
        }

        shuffle(opponents, random);
        avoidBackToBack(opponents);

        boolean homeFirst = random.nextBoolean();
        List<ScheduledSeries> schedule = new ArrayList<>();
        for (int i = 0; i < opponents.size(); i++) {
            boolean home = (i % 2 == 0) == homeFirst;
            schedule.add(new ScheduledSeries(i + 1, opponents.get(i), home));
        }
        return List.copyOf(schedule);
    }

    private static void repeat(List<Long> list, Long id, int times) {
        for (int i = 0; i < times; i++) {
            list.add(id);
        }
    }

    private static <T> void shuffle(List<T> list, RandomGenerator random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    /** 같은 상대가 연속되면 다른 자리의 상대와 바꾼다 (뒤쪽 먼저, 없으면 앞쪽) */
    static void avoidBackToBack(List<Long> list) {
        for (int i = 1; i < list.size(); i++) {
            if (!list.get(i).equals(list.get(i - 1))) {
                continue;
            }
            for (int offset = 1; offset < list.size(); offset++) {
                int j = (i + offset) % list.size();
                if (canSwap(list, i, j)) {
                    Long tmp = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, tmp);
                    break;
                }
            }
        }
    }

    /** i 와 j 를 바꿔도 두 자리 모두 양옆과 겹치지 않는지 */
    private static boolean canSwap(List<Long> list, int i, int j) {
        if (Math.abs(i - j) <= 1) {
            return false;
        }
        Long a = list.get(i);
        Long b = list.get(j);
        return !b.equals(neighbor(list, i - 1)) && !b.equals(neighbor(list, i + 1))
                && !a.equals(neighbor(list, j - 1)) && !a.equals(neighbor(list, j + 1));
    }

    private static Long neighbor(List<Long> list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }
}
