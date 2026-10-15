package com.mlbgame.league.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.world.TeamStrength.RosterSlot;
import com.mlbgame.league.world.WorldGenerator.GeneratedTeam;
import com.mlbgame.simulation.game.TeamRatings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamStrengthTest {

    @Test
    void 타선_선발_불펜을_역할별_평균으로_계산한다() {
        List<RosterSlot> roster = new ArrayList<>();
        for (int i = 0; i < 9; i++) roster.add(new RosterSlot(RosterRole.LINEUP, 70));
        for (int i = 0; i < 4; i++) roster.add(new RosterSlot(RosterRole.BENCH, 50));     // 벤치는 반영 안 함
        for (int i = 0; i < 5; i++) roster.add(new RosterSlot(RosterRole.ROTATION, 66));
        for (int i = 0; i < 7; i++) roster.add(new RosterSlot(RosterRole.BULLPEN, 60));
        roster.add(new RosterSlot(RosterRole.CLOSER, 68));

        TeamRatings ratings = TeamStrength.fromRoster(roster);
        assertThat(ratings.offense()).isEqualTo(70);
        assertThat(ratings.rotation()).isEqualTo(66);
        assertThat(ratings.bullpen()).isEqualTo(61);   // (60×7 + 68) / 8 = 61
    }

    @Test
    void 마이너리그는_레벨_평균에_팜_점수를_더한다() {
        assertThat(TeamStrength.forMinors(LeagueLevel.AA, 60)).isEqualTo(TeamRatings.uniform(52));
        assertThat(TeamStrength.forMinors(LeagueLevel.AA, 90)).isEqualTo(TeamRatings.uniform(55));
        assertThat(TeamStrength.forMinors(LeagueLevel.ROOKIE, 30)).isEqualTo(TeamRatings.uniform(32));
    }

    @Test
    void 생성된_월드의_팀_전력은_메이저_평균_근처에_퍼져_있다() {
        List<GeneratedTeam> world = WorldGenerator.generate(11L, WorldGeneratorTest.teams());
        List<Double> overall = world.stream()
                .map(t -> TeamStrength.fromRoster(t.roster().stream()
                        .map(n -> new RosterSlot(n.role(), n.overall())).toList()).overall())
                .toList();
        double min = overall.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double max = overall.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double avg = overall.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        assertThat(avg).isBetween(62.0, 69.0);
        assertThat(max - min).as("강팀과 약팀의 차이").isGreaterThan(4.0);
    }
}
