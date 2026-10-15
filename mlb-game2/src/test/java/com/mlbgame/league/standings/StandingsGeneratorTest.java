package com.mlbgame.league.standings;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.Team;
import com.mlbgame.league.standings.StandingsTable.PlayoffSeed;
import com.mlbgame.season.schedule.ScheduleGeneratorTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandingsGeneratorTest {

    final List<Team> teams = ScheduleGeneratorTest.teams();

    Map<Long, Double> ratings(double strong) {
        Map<Long, Double> ratings = new HashMap<>();
        teams.forEach(t -> ratings.put(t.getId(), 65.0));
        ratings.put(1L, strong);
        return ratings;
    }

    @Test
    void 리그_전체_승수와_패수가_같고_팀당_경기_수가_맞다() {
        Map<Long, int[]> records = StandingsGenerator.simulate(1L, teams, ratings(65), 54);
        int wins = records.values().stream().mapToInt(r -> r[0]).sum();
        int losses = records.values().stream().mapToInt(r -> r[1]).sum();
        assertThat(wins).isEqualTo(losses);
        assertThat(records.values()).allSatisfy(r -> assertThat(r[0] + r[1]).isEqualTo(162));
    }

    @Test
    void 강팀이_더_많이_이긴다() {
        int strongWins = 0;
        for (long seed = 0; seed < 20; seed++) {
            strongWins += StandingsGenerator.simulate(seed, teams, ratings(75), 54).get(1L)[0];
        }
        assertThat(strongWins / 20.0).isGreaterThan(95);   // 162경기 중 약 .600 이상
    }

    @Test
    void 내_팀의_실제_결과로_바꿀_수_있다() {
        Map<Integer, int[]> actual = Map.of(1, new int[]{3, 0}, 2, new int[]{0, 3});
        Map<Long, int[]> records = StandingsGenerator.simulate(5L, teams, ratings(65), 2, 1L, actual);
        assertThat(records.get(1L)).containsExactly(3, 3);
        int wins = records.values().stream().mapToInt(r -> r[0]).sum();
        int losses = records.values().stream().mapToInt(r -> r[1]).sum();
        assertThat(wins).isEqualTo(losses);
    }

    @Test
    void 리그마다_지구_우승_3팀과_와일드카드_3팀이_시드를_받는다() {
        StandingsTable table = StandingsGenerator.table(teams, StandingsGenerator.simulate(3L, teams, ratings(65), 54));
        for (String league : StandingsTable.LEAGUES) {
            List<PlayoffSeed> seeds = table.playoffSeeds(league);
            assertThat(seeds).hasSize(6);
            assertThat(seeds.subList(0, 3)).allMatch(PlayoffSeed::divisionWinner);
            assertThat(seeds.subList(3, 6)).noneMatch(PlayoffSeed::divisionWinner);
            assertThat(seeds).allMatch(s -> s.row().team().getLeague().equals(league));
            assertThat(seeds.stream().map(s -> s.row().team().getDivision()).limit(3).distinct().count()).isEqualTo(3);
        }
    }

    @Test
    void 지구_선두의_승차는_0이다() {
        StandingsTable table = StandingsGenerator.table(teams, StandingsGenerator.simulate(4L, teams, ratings(65), 20));
        var division = table.division("AL", "EAST");
        assertThat(division).hasSize(5);
        assertThat(division.get(0).gamesBehind()).isZero();
        assertThat(division.get(4).gamesBehind()).isGreaterThanOrEqualTo(0);
    }
}
