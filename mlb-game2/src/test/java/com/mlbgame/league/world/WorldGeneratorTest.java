package com.mlbgame.league.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mlbgame.league.Team;
import com.mlbgame.league.world.WorldGenerator.GeneratedNpc;
import com.mlbgame.league.world.WorldGenerator.GeneratedTeam;
import com.mlbgame.player.Position;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

    static final List<String> CODES = List.of(
            "BAL", "BOS", "NYY", "TB", "TOR", "CWS", "CLE", "DET", "KC", "MIN",
            "HOU", "LAA", "ATH", "SEA", "TEX", "ATL", "MIA", "NYM", "PHI", "WSH",
            "CHC", "CIN", "MIL", "PIT", "STL", "ARI", "COL", "LAD", "SD", "SF");

    static List<Team> teams() {
        return CODES.stream().map(code -> {
            Team team = mock(Team.class);
            when(team.getCode()).thenReturn(code);
            return team;
        }).toList();
    }

    final List<Team> teams = teams();

    @Test
    void 같은_시드면_같은_월드가_나온다() {
        List<GeneratedTeam> a = WorldGenerator.generate(42L, teams);
        List<GeneratedTeam> b = WorldGenerator.generate(42L, teams);
        for (int i = 0; i < a.size(); i++) {
            assertThat(a.get(i).budget()).isEqualTo(b.get(i).budget());
            assertThat(a.get(i).roster().stream().map(GeneratedNpc::name).toList())
                    .isEqualTo(b.get(i).roster().stream().map(GeneratedNpc::name).toList());
        }
        assertThat(WorldGenerator.generate(43L, teams).get(0).roster().get(0).name())
                .isNotNull();
    }

    @Test
    void 구단마다_26명_로스터가_역할별로_채워진다() {
        for (GeneratedTeam team : WorldGenerator.generate(1L, teams)) {
            assertThat(team.roster()).hasSize(RosterRole.ROSTER_SIZE);
            Map<RosterRole, Long> byRole = team.roster().stream()
                    .collect(Collectors.groupingBy(GeneratedNpc::role, Collectors.counting()));
            for (RosterRole role : RosterRole.values()) {
                assertThat(byRole.get(role)).as(role.name()).isEqualTo(role.getSlots());
            }
            assertThat(team.roster().stream().filter(n -> n.role() == RosterRole.CLOSER))
                    .allMatch(n -> n.position() == Position.CP);
            assertThat(team.roster().stream().filter(n -> n.role() == RosterRole.LINEUP))
                    .anyMatch(n -> n.position() == Position.C)
                    .anyMatch(n -> n.position() == Position.SS);
        }
    }

    @Test
    void 능력치는_11개이고_평균이_OVR_근처다() {
        for (GeneratedTeam team : WorldGenerator.generate(2L, teams)) {
            for (GeneratedNpc npc : team.roster()) {
                assertThat(npc.attributes()).hasSize(11);
                for (int value : npc.attributes()) {
                    assertThat(value).isBetween(20, 99);
                }
                assertThat(npc.overall()).isBetween(33.0, 94.0);
                assertThat(npc.potential()).isGreaterThanOrEqualTo((int) Math.floor(npc.overall()) - 1);
                assertThat(npc.age()).isBetween(22, 37);
            }
        }
    }

    @Test
    void 재정_규모가_실제_시장_규모를_따른다() {
        Map<String, GeneratedTeam> byCode = WorldGenerator.generate(3L, teams).stream()
                .collect(Collectors.toMap(t -> t.team().getCode(), Function.identity()));
        assertThat(byCode.get("NYY").market()).isEqualTo(Market.BIG);
        assertThat(byCode.get("TB").market()).isEqualTo(Market.SMALL);
        assertThat(byCode.get("DET").market()).isEqualTo(Market.MID);
        assertThat(byCode.get("NYY").budget()).isGreaterThan(byCode.get("TB").budget());
    }

    @Test
    void 빅마켓이_평균적으로_더_강하고_페이롤도_현실적인_규모다() {
        double bigOvr = 0;
        double smallOvr = 0;
        double payroll = 0;
        int worlds = 50;
        for (long seed = 0; seed < worlds; seed++) {
            for (GeneratedTeam team : WorldGenerator.generate(seed, teams)) {
                double avg = team.roster().stream().mapToDouble(GeneratedNpc::overall).average().orElse(0);
                if (team.market() == Market.BIG) bigOvr += avg;
                if (team.market() == Market.SMALL) smallOvr += avg;
                payroll += team.payroll();
            }
        }
        assertThat(bigOvr / (8 * worlds)).isGreaterThan(smallOvr / (8 * worlds));
        double avgPayroll = payroll / (30.0 * worlds);
        assertThat(avgPayroll).isBetween(70_000_000.0, 220_000_000.0);
    }

    @Test
    void 연봉은_서비스_연수_규칙을_따른다() {
        assertThat(Payroll.salaryByService(0, 80)).isEqualTo(Payroll.MINIMUM_SALARY);
        assertThat(Payroll.salaryByService(3, 65)).isEqualTo(3_200_000L);   // 시장가 8M × 40%
        assertThat(Payroll.salaryByService(6, 65)).isEqualTo(8_000_000L);
        assertThat(Payroll.marketValue(85)).isBetween(35_000_000L, 37_000_000L);
        assertThat(Payroll.marketValue(30)).isEqualTo(Payroll.MINIMUM_SALARY);
    }

    @Test
    void NPC_능력치는_한_칸에_저장했다가_되살릴_수_있다() {
        int[] values = {65, 70, 58, 61, 77, 40, 55, 66, 72, 68, 63};
        String encoded = NpcPlayer.encode(values);
        assertThat(encoded).isEqualTo("65,70,58,61,77,40,55,66,72,68,63");
        assertThat(encoded.length()).isLessThanOrEqualTo(60);
    }
}
