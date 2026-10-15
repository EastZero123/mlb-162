package com.mlbgame.simulation.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.world.RosterRole;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.PlateAppearanceModel;
import com.mlbgame.simulation.PlateAppearanceModelTest;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class NpcStatGeneratorTest {

    final NpcStatGenerator generator = new NpcStatGenerator(new PlateAppearanceModel(PlateAppearanceModelTest.defaultBalance()));

    static PlayerSnapshot snapshot(Position position, int rating) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        Attribute.forPosition(position).forEach(a -> attributes.put(a, rating));
        return new PlayerSnapshot(position, Hand.R, Hand.R, attributes, List.of());
    }

    /** 6개월 × 27경기 = 162경기 시즌 */
    private BattingLine hitterSeason(int rating, RosterRole role, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        BattingLine total = new BattingLine();
        for (int m = 0; m < 6; m++) {
            total.add(generator.hitter(snapshot(Position.SS, rating), role, 27, rng).batting());
        }
        return total;
    }

    private PitchingLine pitcherSeason(Position position, int rating, RosterRole role, int slot, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        PitchingLine total = new PitchingLine();
        for (int m = 0; m < 6; m++) {
            total.add(generator.pitcher(snapshot(position, rating), role, slot, 27, 0.5, rng));
        }
        return total;
    }

    @Test
    void 평균_주전_타자의_시즌_기록이_MLB_수준이다() {
        BattingLine total = new BattingLine();
        int seasons = 30;
        for (int s = 0; s < seasons; s++) {
            total.add(hitterSeason(65, RosterRole.LINEUP, s));
        }
        assertThat(total.plateAppearances / (double) seasons).isBetween(580.0, 680.0);
        assertThat(total.getAverage()).isBetween(0.232, 0.262);
        assertThat(total.homeRuns / (double) seasons).isBetween(13.0, 25.0);
        assertThat(total.runs / (double) seasons).isBetween(55.0, 95.0);
        assertThat(total.rbi / (double) seasons).isBetween(55.0, 95.0);
    }

    @Test
    void 능력치가_높으면_기록이_좋고_벤치는_타석이_적다() {
        BattingLine star = hitterSeason(85, RosterRole.LINEUP, 1);
        BattingLine average = hitterSeason(65, RosterRole.LINEUP, 1);
        BattingLine bench = hitterSeason(65, RosterRole.BENCH, 1);

        assertThat(star.getOps()).isGreaterThan(average.getOps());
        assertThat(bench.plateAppearances).isLessThan(average.plateAppearances / 3);
    }

    @Test
    void 평균_선발투수는_시즌_30선발_안팎에_MLB_수준_ERA다() {
        PitchingLine total = new PitchingLine();
        int seasons = 30;
        for (int s = 0; s < seasons; s++) {
            total.add(pitcherSeason(Position.SP, 65, RosterRole.ROTATION, 3, s));
        }
        assertThat(total.gamesStarted / (double) seasons).isBetween(30.0, 34.0);
        assertThat(total.outs / 3.0 / seasons).isBetween(140.0, 210.0);
        assertThat(total.getEra()).isBetween(3.4, 5.4);
        assertThat(total.wins + total.losses).isPositive();
    }

    @Test
    void 마무리는_세이브를_쌓고_불펜은_세이브가_없다() {
        PitchingLine closer = new PitchingLine();
        PitchingLine reliever = new PitchingLine();
        for (int s = 0; s < 20; s++) {
            closer.add(pitcherSeason(Position.CP, 65, RosterRole.CLOSER, 0, s));
            reliever.add(pitcherSeason(Position.RP, 65, RosterRole.BULLPEN, 0, s));
        }
        assertThat(closer.saves / 20.0).isBetween(18.0, 45.0);
        assertThat(closer.games / 20.0).isBetween(55.0, 80.0);
        assertThat(reliever.saves).isZero();
        assertThat(closer.outs / 3.0 / closer.games).isLessThanOrEqualTo(1.01);
    }

    @Test
    void 같은_시드면_같은_기록이다() {
        assertThat(hitterSeason(70, RosterRole.LINEUP, 99).summary())
                .isEqualTo(hitterSeason(70, RosterRole.LINEUP, 99).summary());
    }
}
