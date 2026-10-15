package com.mlbgame.league.standings;

import com.mlbgame.league.Team;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * 메이저리그 30개 구단 순위 생성 (경기를 돌리지 않는 통계 방식, 저장 없음 — 같은 시드면 같은 순위).
 *
 * <pre>
 * 시리즈 차수마다 30팀을 무작위로 15쌍 짝지어 3경기씩
 * 경기 승률 = 1 / (1 + e^(−0.06 × (A 전력 − B 전력)))   (전력 10 차이 → 약 .646)
 * </pre>
 * 짝을 지어 진행하므로 리그 전체 승수 = 패수가 항상 맞는다.
 */
public final class StandingsGenerator {

    static final double WIN_K = 0.06;
    static final int GAMES_PER_SERIES = 3;

    private StandingsGenerator() {
    }

    public static long seed(long worldSeed, int year) {
        return worldSeed * 17 + year * 7_919L;
    }

    /** 1번 ~ seriesCount 번 시리즈까지의 구단별 [승, 패] */
    public static Map<Long, int[]> simulate(long seed, List<Team> teams, Map<Long, Double> ratings, int seriesCount) {
        return simulate(seed, teams, ratings, seriesCount, null, Map.of());
    }

    /**
     * @param actualTeamId 실제 결과로 바꿀 구단 (내 선수가 메이저에서 뛴 팀), 없으면 null
     * @param actual       시리즈 번호 → 그 팀의 실제 [승, 패]. 짝지어진 상대에게는 반대 결과를 준다
     */
    public static Map<Long, int[]> simulate(long seed, List<Team> teams, Map<Long, Double> ratings, int seriesCount,
                                            Long actualTeamId, Map<Integer, int[]> actual) {
        Map<Long, int[]> records = new HashMap<>();
        teams.forEach(t -> records.put(t.getId(), new int[2]));
        for (int s = 1; s <= seriesCount; s++) {
            RandomGenerator rng = new SplittableRandom(seed * 31 + s);
            List<Long> ids = new ArrayList<>(teams.stream().map(Team::getId).toList());
            for (int i = ids.size() - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                Long tmp = ids.get(i);
                ids.set(i, ids.get(j));
                ids.set(j, tmp);
            }
            for (int i = 0; i + 1 < ids.size(); i += 2) {
                Long a = ids.get(i);
                Long b = ids.get(i + 1);
                double p = winProbability(ratings.getOrDefault(a, 65.0), ratings.getOrDefault(b, 65.0));
                int aWins = 0;
                for (int g = 0; g < GAMES_PER_SERIES; g++) {
                    if (rng.nextDouble() < p) aWins++;             // 실제 결과로 바꾸더라도 난수는 똑같이 소비
                }
                int[] override = actual.get(s);
                if (override != null && a.equals(actualTeamId)) {
                    aWins = override[0];
                } else if (override != null && b.equals(actualTeamId)) {
                    aWins = override[1];
                }
                records.get(a)[0] += aWins;
                records.get(a)[1] += GAMES_PER_SERIES - aWins;
                records.get(b)[0] += GAMES_PER_SERIES - aWins;
                records.get(b)[1] += aWins;
            }
        }
        return records;
    }

    public static double winProbability(double ratingA, double ratingB) {
        return 1 / (1 + Math.exp(-WIN_K * (ratingA - ratingB)));
    }

    public static StandingsTable table(List<Team> teams, Map<Long, int[]> records) {
        List<StandingsTable.Row> rows = teams.stream()
                .map(t -> new StandingsTable.Row(t, records.get(t.getId())[0], records.get(t.getId())[1]))
                .sorted(Comparator.comparingDouble(StandingsTable.Row::pct).reversed()
                        .thenComparing(r -> r.team().getId()))
                .toList();
        return new StandingsTable(rows);
    }
}
