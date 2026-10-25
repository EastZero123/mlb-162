package com.mlbgame.league.standings;

import com.mlbgame.league.Team;
import java.util.ArrayList;
import java.util.List;

/**
 * 순위표. 행은 승률 내림차순. 지구별 순위, 승차, 포스트시즌 시드(리그당 6팀)를 계산한다.
 */
public record StandingsTable(List<Row> rows) {

    public static final List<String> LEAGUES = List.of("AL", "NL");
    public static final List<String> DIVISIONS = List.of("EAST", "CENTRAL", "WEST");
    public static final int DIVISION_WINNERS = 3;
    public static final int WILD_CARDS = 3;

    public record Row(Team team, int wins, int losses) {
        public double pct() {
            int games = wins + losses;
            return games == 0 ? 0 : (double) wins / games;
        }
    }

    /** 승차 표시용 행 */
    public record DivisionRow(Row row, double gamesBehind) {
    }

    /**
     * 포스트시즌 시드.
     *
     * @param seed           1~6 (1·2번 시드는 와일드카드 시리즈 부전승)
     * @param divisionWinner 지구 우승 여부
     */
    public record PlayoffSeed(int seed, Row row, boolean divisionWinner) {
    }

    /** 리그 전체 한 표 (지구가 없는 KBO) — 승차는 1위 기준 */
    public List<DivisionRow> overall() {
        if (rows.isEmpty()) {
            return List.of();
        }
        Row leader = rows.get(0);
        return rows.stream()
                .map(r -> new DivisionRow(r, ((leader.wins() - r.wins()) + (r.losses() - leader.losses())) / 2.0))
                .toList();
    }

    public List<DivisionRow> division(String league, String division) {
        List<Row> teams = rows.stream()
                .filter(r -> r.team().getLeague().equals(league) && r.team().getDivision().equals(division))
                .toList();
        if (teams.isEmpty()) {
            return List.of();
        }
        Row leader = teams.get(0);
        return teams.stream()
                .map(r -> new DivisionRow(r, ((leader.wins() - r.wins()) + (r.losses() - leader.losses())) / 2.0))
                .toList();
    }

    /** 지구 우승 3팀(승률순 1~3번 시드) + 와일드카드 3팀(4~6번 시드) */
    public List<PlayoffSeed> playoffSeeds(String league) {
        List<Row> winners = new ArrayList<>();
        for (String division : DIVISIONS) {
            List<DivisionRow> standings = division(league, division);
            if (!standings.isEmpty()) {
                winners.add(standings.get(0).row());
            }
        }
        winners.sort((a, b) -> Double.compare(b.pct(), a.pct()));
        List<Row> wildCards = rows.stream()
                .filter(r -> r.team().getLeague().equals(league) && !winners.contains(r))
                .limit(WILD_CARDS)
                .toList();
        List<PlayoffSeed> seeds = new ArrayList<>();
        for (int i = 0; i < winners.size(); i++) {
            seeds.add(new PlayoffSeed(i + 1, winners.get(i), true));
        }
        for (int i = 0; i < wildCards.size(); i++) {
            seeds.add(new PlayoffSeed(winners.size() + i + 1, wildCards.get(i), false));
        }
        return seeds;
    }

    public Row of(Long teamId) {
        return rows.stream().filter(r -> r.team().getId().equals(teamId)).findFirst().orElse(null);
    }
}
