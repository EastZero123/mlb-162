package com.mlbgame.season.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mlbgame.league.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ScheduleGeneratorTest {

    /** AL/NL × 동·중·서 지구 × 5팀 = 30팀 (다른 테스트에서도 사용) */
    public static List<Team> teams() {
        List<Team> teams = new ArrayList<>();
        long id = 1;
        for (String league : List.of("AL", "NL")) {
            for (String division : List.of("EAST", "CENTRAL", "WEST")) {
                for (int i = 0; i < 5; i++) {
                    Team team = mock(Team.class);
                    when(team.getId()).thenReturn(id++);
                    when(team.getLeague()).thenReturn(league);
                    when(team.getDivision()).thenReturn(division);
                    teams.add(team);
                }
            }
        }
        return teams;
    }

    final List<Team> teams = teams();
    final Team me = teams.get(0);   // AL 동부
    final Map<Long, Team> byId = teams.stream().collect(Collectors.toMap(Team::getId, Function.identity()));

    @Test
    void 시즌은_54시리즈이고_홈과_원정이_반반이다() {
        List<ScheduledSeries> schedule = ScheduleGenerator.generate(1L, me, teams);
        assertThat(schedule).hasSize(54);
        assertThat(schedule.stream().filter(ScheduledSeries::home).count()).isEqualTo(27);
        assertThat(schedule).extracting(ScheduledSeries::seriesNo).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 54).boxed().toList());
        assertThat(schedule).noneMatch(s -> s.opponentId().equals(me.getId()));
    }

    @Test
    void 같은_지구는_4번_같은_리그는_2번_인터리그는_1번_이상_만난다() {
        Map<Long, Long> meetings = ScheduleGenerator.generate(2L, me, teams).stream()
                .collect(Collectors.groupingBy(ScheduledSeries::opponentId, Collectors.counting()));
        int extra = 0;
        for (Map.Entry<Long, Long> e : meetings.entrySet()) {
            Team opponent = byId.get(e.getKey());
            if (!opponent.getLeague().equals(me.getLeague())) {
                assertThat(e.getValue()).isBetween(1L, 2L);
                if (e.getValue() == 2) extra++;
            } else if (opponent.getDivision().equals(me.getDivision())) {
                assertThat(e.getValue()).isEqualTo(4L);
            } else {
                assertThat(e.getValue()).isEqualTo(2L);
            }
        }
        assertThat(meetings).hasSize(29);
        assertThat(extra).isEqualTo(3);
    }

    @Test
    void 같은_상대와_연속_시리즈를_하지_않는다() {
        for (long seed = 0; seed < 50; seed++) {
            List<ScheduledSeries> schedule = ScheduleGenerator.generate(seed, me, teams);
            for (int i = 1; i < schedule.size(); i++) {
                assertThat(schedule.get(i).opponentId()).as("seed " + seed + " #" + (i + 1))
                        .isNotEqualTo(schedule.get(i - 1).opponentId());
            }
        }
    }

    @Test
    void 같은_시드면_같은_일정이다() {
        assertThat(ScheduleGenerator.generate(7L, me, teams)).isEqualTo(ScheduleGenerator.generate(7L, me, teams));
        assertThat(ScheduleGenerator.generate(7L, me, teams)).isNotEqualTo(ScheduleGenerator.generate(8L, me, teams));
    }

    @Test
    void 시리즈_번호로_월이_정해진다() {
        assertThat(new ScheduledSeries(1, 2L, true).month()).isEqualTo(4);
        assertThat(new ScheduledSeries(9, 2L, true).month()).isEqualTo(4);
        assertThat(new ScheduledSeries(10, 2L, true).month()).isEqualTo(5);
        assertThat(new ScheduledSeries(54, 2L, true).month()).isEqualTo(9);
    }
}
