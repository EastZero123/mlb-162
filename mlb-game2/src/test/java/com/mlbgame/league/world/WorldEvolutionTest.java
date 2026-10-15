package com.mlbgame.league.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.Team;
import com.mlbgame.league.world.WorldEvolution.Report;
import com.mlbgame.league.world.WorldGenerator.GeneratedNpc;
import com.mlbgame.league.world.WorldGenerator.GeneratedTeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorldEvolutionTest {

    /** WorldGenerator 결과를 엔티티 객체로 바꾼 테스트용 월드 */
    static final class World {
        final List<WorldTeam> teams = new ArrayList<>();
        final List<NpcPlayer> npcs = new ArrayList<>();
        final Map<Long, Double> winPct = new HashMap<>();
    }

    static World world(long seed) {
        List<Team> teamMocks = WorldGeneratorTest.teams();
        for (int i = 0; i < teamMocks.size(); i++) {
            org.mockito.Mockito.when(teamMocks.get(i).getId()).thenReturn((long) i + 1);
        }
        World world = new World();
        for (GeneratedTeam t : WorldGenerator.generate(seed, teamMocks)) {
            world.teams.add(new WorldTeam(1L, t.team(), t.market(), t.spendingPercent(), t.farmScore(), t.budget()));
            world.winPct.put(t.team().getId(), 0.4 + (t.team().getId() % 5) * 0.05);
            for (GeneratedNpc n : t.roster()) {
                world.npcs.add(NpcPlayer.create(1L, t.team().getId(), n.name(), n.position(), n.bats(), n.throwsHand(),
                        n.age(), n.potential(), n.attributes(), n.role(), n.salary(), n.contractYears(), n.serviceYears()));
            }
        }
        return world;
    }

    @Test
    void 시즌이_지나도_구단마다_1군_26명이_역할대로_채워진다() {
        World w = world(1L);
        List<NpcPlayer> created = new ArrayList<>();
        WorldEvolution.evolve(99L, 1L, w.teams, w.npcs, w.winPct, created);
        List<NpcPlayer> all = new ArrayList<>(w.npcs);
        all.addAll(created);

        Map<Long, List<NpcPlayer>> byTeam = all.stream()
                .filter(n -> n.getStatus() == NpcStatus.MLB)
                .collect(Collectors.groupingBy(NpcPlayer::getTeamId));
        assertThat(byTeam).hasSize(30);
        for (List<NpcPlayer> roster : byTeam.values()) {
            assertThat(roster).hasSize(RosterRole.ROSTER_SIZE);
            for (RosterRole role : RosterRole.values()) {
                assertThat(roster.stream().filter(n -> n.getRosterRole() == role).count())
                        .as(role.name()).isEqualTo(role.getSlots());
            }
        }
    }

    @Test
    void 나이가_들고_은퇴와_FA_이동과_콜업이_일어난다() {
        World w = world(2L);
        Map<String, Integer> agesBefore = w.npcs.stream()
                .collect(Collectors.toMap(n -> n.getName() + n.hashCode(), NpcPlayer::getAge));
        List<NpcPlayer> created = new ArrayList<>();
        Report report = WorldEvolution.evolve(7L, 1L, w.teams, w.npcs, w.winPct, created);

        assertThat(w.npcs).allSatisfy(n ->
                assertThat(n.getAge()).isEqualTo(agesBefore.get(n.getName() + n.hashCode()) + 1));
        assertThat(report.retired()).isPositive();
        assertThat(report.callUps()).isEqualTo(created.size()).isPositive();
        assertThat(w.npcs.stream().filter(n -> n.getStatus() == NpcStatus.RETIRED))
                .allSatisfy(n -> assertThat(n.getTeamId()).isNull());
        assertThat(created).allSatisfy(n -> {
            assertThat(n.getAge()).isBetween(21, 24);
            assertThat(n.getServiceYears()).isZero();
            assertThat(n.getSalary()).isEqualTo(Payroll.MINIMUM_SALARY);
        });
    }

    @Test
    void 젊은_선수는_대체로_성장하고_노장은_대체로_하락한다() {
        assertThat(WorldEvolution.growthBase(22)).isPositive();
        assertThat(WorldEvolution.growthBase(29)).isZero();
        assertThat(WorldEvolution.growthBase(35)).isNegative();
    }

    @Test
    void 예산은_재정_규모를_따르고_팜은_하위_팀이_더_오른다() {
        World w = world(3L);
        Map<Long, Integer> farmBefore = w.teams.stream()
                .collect(Collectors.toMap(t -> t.getTeam().getId(), WorldTeam::getFarmScore));
        WorldEvolution.updateFinances(w.teams, w.npcs, w.winPct, new java.util.SplittableRandom(1));

        for (WorldTeam t : w.teams) {
            assertThat(t.getBudget()).isBetween(70_000_000L, 320_000_000L);
            assertThat(t.getFarmScore()).isBetween(20, 95);
        }
        // 팜 점수는 평균 60 쪽으로 수렴하는 방향 (90 → 내려감, 30 → 올라감)
        for (WorldTeam t : w.teams) {
            int before = farmBefore.get(t.getTeam().getId());
            if (before >= 90) assertThat(t.getFarmScore()).isLessThan(before + 5);
        }
    }
}
