package com.mlbgame.simulation.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.award.AwardRules;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.world.RosterRole;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.PlateAppearanceModel;
import com.mlbgame.simulation.PlateAppearanceModelTest;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.balance.SeasonHarness.Season;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.stats.NpcStatGenerator;
import com.mlbgame.simulation.stats.NpcStatGenerator.HitterLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 시상 공정성: 같은 OVR 이면 내 선수(경기 시뮬레이션)와 NPC(통계 생성)의 시상 점수가 비슷해야 한다.
 * 한쪽 경로만 출전량(타석·이닝)이 많거나 기록이 좋으면, 그 포지션만 MVP·사이영을 쉽게 받는다 (M5-3 에서 발견:
 * 비슷한 OVR 에서 대졸 유격수 MVP 0회, 대졸 선발 사이영 6~8회).
 */
@Tag("balance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AwardParityBalanceTest {

    static final int SEASONS = 120;
    static final int TEAM_GAMES = 162;
    static final int[] RATINGS = {65, 75, 85, 90};

    record Row(double userScore, double npcScore, double userVolume, double npcVolume, double userRate, double npcRate) {
        double ratio() {
            return userScore / npcScore;
        }
    }

    final SeasonHarness harness = new SeasonHarness();
    final NpcStatGenerator generator = new NpcStatGenerator(new PlateAppearanceModel(PlateAppearanceModelTest.defaultBalance()));
    final Map<Integer, Row> hitters = new TreeMap<>();
    final Map<Integer, Row> starters = new TreeMap<>();

    @BeforeAll
    void simulate() {
        for (int rating : RATINGS) {
            hitters.put(rating, hitterRow(rating));
            starters.put(rating, starterRow(rating));
        }
        System.out.println();
        System.out.println("===== 시상 점수: 내 선수(경기 시뮬레이션) vs NPC(통계 생성), 시즌 평균 =====");
        System.out.printf("%-10s %10s %10s %7s %8s %8s %8s %8s%n", "구분", "내 점수", "NPC 점수", "비율", "내 출전", "NPC 출전", "내 기록", "NPC 기록");
        hitters.forEach((r, row) -> print("SS OVR " + r, row, "타석", "OPS"));
        starters.forEach((r, row) -> print("SP OVR " + r, row, "이닝", "ERA"));
        System.out.println("(타자 출전 = 타석, 기록 = OPS / 선발 출전 = 이닝, 기록 = ERA)");
        System.out.println();
    }

    private void print(String label, Row row, String volume, String rate) {
        System.out.printf("%-10s %10.1f %10.1f %7.2f %8.1f %8.1f %8.3f %8.3f%n", label, row.userScore(), row.npcScore(),
                row.ratio(), row.userVolume(), row.npcVolume(), row.userRate(), row.npcRate());
    }

    private Row hitterRow(int rating) {
        PlayerSnapshot player = SeasonHarness.player(Position.SS, rating);
        List<Season> user = harness.seasons(player, LeagueLevel.MLB, SEASONS, 10_000L + rating);
        SplittableRandom rng = new SplittableRandom(20_000L + rating);
        List<HitterLine> npc = new ArrayList<>();
        for (int i = 0; i < SEASONS; i++) {
            npc.add(generator.hitter(player, RosterRole.LINEUP, TEAM_GAMES, rng));
        }
        return new Row(
                SeasonHarness.mean(user, s -> AwardRules.hitter(s.batting(), s.fielding())),
                npc.stream().mapToDouble(l -> AwardRules.hitter(l.batting(), l.fielding())).average().orElse(0),
                SeasonHarness.mean(user, s -> s.batting().plateAppearances),
                npc.stream().mapToDouble(l -> l.batting().plateAppearances).average().orElse(0),
                SeasonHarness.totalBatting(user).getOps(),
                npc.stream().mapToDouble(l -> l.batting().getOps()).average().orElse(0));
    }

    private Row starterRow(int rating) {
        PlayerSnapshot player = SeasonHarness.player(Position.SP, rating);
        List<Season> user = harness.seasons(player, LeagueLevel.MLB, SEASONS, 30_000L + rating);
        SplittableRandom rng = new SplittableRandom(40_000L + rating);
        List<PitchingLine> npc = new ArrayList<>();
        for (int i = 0; i < SEASONS; i++) {
            npc.add(generator.pitcher(player, RosterRole.ROTATION, 1, TEAM_GAMES, 0.5, rng));
        }
        return new Row(
                SeasonHarness.mean(user, s -> AwardRules.starter(s.pitching())),
                npc.stream().mapToDouble(AwardRules::starter).average().orElse(0),
                SeasonHarness.mean(user, s -> s.pitching().outs / 3.0),
                npc.stream().mapToDouble(p -> p.outs / 3.0).average().orElse(0),
                SeasonHarness.totalPitching(user).getEra(),
                npc.stream().mapToDouble(PitchingLine::getEra).average().orElse(0));
    }

    @Test
    void 같은_OVR이면_타자_시상_점수가_비슷하다() {
        assertThat(hitters.get(85).ratio()).as("SS OVR 85 내/NPC").isBetween(0.85, 1.15);
        assertThat(hitters.get(90).ratio()).as("SS OVR 90 내/NPC").isBetween(0.85, 1.15);
    }

    @Test
    void 같은_OVR이면_선발_시상_점수가_비슷하다() {
        assertThat(starters.get(85).ratio()).as("SP OVR 85 내/NPC").isBetween(0.85, 1.15);
        assertThat(starters.get(90).ratio()).as("SP OVR 90 내/NPC").isBetween(0.85, 1.15);
    }
}
