package com.mlbgame.simulation.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.player.Hand;
import com.mlbgame.player.PitchType;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.BatterProfile;
import com.mlbgame.simulation.PitcherProfile;
import com.mlbgame.simulation.PlateAppearanceModel;
import com.mlbgame.simulation.PlateAppearanceModelTest;
import com.mlbgame.simulation.SimulationBalance;
import com.mlbgame.simulation.game.UserHitter.HitterRole;
import com.mlbgame.simulation.game.UserPitcher.PitcherRole;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class GameSimulatorTest {

    final SimulationBalance balance = PlateAppearanceModelTest.defaultBalance();
    final GameSimulator simulator = new GameSimulator(new PlateAppearanceModel(balance), balance);

    static UserHitter hitter(int rating, int slot, HitterRole role, Position position) {
        return new UserHitter(BatterProfile.average(rating).withBats(Hand.R), rating, rating, rating,
                position, slot, role, 0);
    }

    static UserPitcher pitcher(int rating, PitcherRole role) {
        return new UserPitcher(PitcherProfile.average(rating).withThrows(Hand.R), rating, rating, role, 0,
                List.of(PitchType.FOUR_SEAM, PitchType.SLIDER));
    }

    @Test
    void 같은_시드면_같은_경기가_나온다() {
        GameSetup setup = GameSetup.forHitter(true, 65, 65, hitter(70, 3, HitterRole.STARTER, Position.SS));
        GameResult a = simulator.simulate(setup, new SplittableRandom(42));
        GameResult b = simulator.simulate(setup, new SplittableRandom(42));

        assertThat(a.teamScore()).isEqualTo(b.teamScore());
        assertThat(a.opponentScore()).isEqualTo(b.opponentScore());
        assertThat(a.teamLine()).isEqualTo(b.teamLine());
        assertThat(a.batting().summary()).isEqualTo(b.batting().summary());
        assertThat(a.highlights()).isEqualTo(b.highlights());
    }

    @Test
    void 경기는_항상_승부가_나고_9회_이상_진행된다() {
        SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < 500; i++) {
            GameResult r = simulator.simulate(
                    GameSetup.forHitter(i % 2 == 0, 65, 65, hitter(65, 4, HitterRole.STARTER, Position.OF)), random);
            assertThat(r.teamScore()).isNotEqualTo(r.opponentScore());
            assertThat(r.innings()).isGreaterThanOrEqualTo(9);
            assertThat(r.teamLine().stream().mapToInt(Integer::intValue).sum()).isEqualTo(r.teamScore());
        }
    }

    @Test
    void 평균_팀끼리면_경기당_득점이_MLB_수준이다() {
        SplittableRandom random = new SplittableRandom(2);
        int games = 3000;
        long runs = 0;
        for (int i = 0; i < games; i++) {
            GameResult r = simulator.simulate(
                    GameSetup.forHitter(i % 2 == 0, 65, 65, hitter(65, 5, HitterRole.STARTER, Position.B1)), random);
            runs += r.teamScore() + r.opponentScore();
        }
        double perTeam = runs / (2.0 * games);
        assertThat(perTeam).isBetween(3.6, 5.4);   // MLB 평균 약 4.5점
    }

    @Test
    void 강팀이_더_많이_이긴다() {
        SplittableRandom random = new SplittableRandom(3);
        int wins = 0;
        for (int i = 0; i < 1000; i++) {
            GameResult r = simulator.simulate(
                    GameSetup.forHitter(true, 75, 55, hitter(75, 3, HitterRole.STARTER, Position.SS)), random);
            if (r.isWin()) wins++;
        }
        assertThat(wins / 1000.0).isGreaterThan(0.6);
    }

    @Test
    void 선발_타자는_경기당_4타석_안팎을_소화한다() {
        SplittableRandom random = new SplittableRandom(4);
        BattingLine total = new BattingLine();
        for (int i = 0; i < 500; i++) {
            total.add(simulator.simulate(
                    GameSetup.forHitter(false, 65, 65, hitter(65, 3, HitterRole.STARTER, Position.B3)), random).batting());
        }
        assertThat(total.games).isEqualTo(500);
        assertThat(total.plateAppearances / 500.0).isBetween(3.9, 4.9);
        assertThat(total.getAverage()).isBetween(0.20, 0.30);
    }

    @Test
    void 대타는_한_타석만_결장은_출전하지_않는다() {
        SplittableRandom random = new SplittableRandom(5);
        for (int i = 0; i < 200; i++) {
            GameResult pinch = simulator.simulate(
                    GameSetup.forHitter(true, 65, 65, hitter(65, 0, HitterRole.PINCH_HITTER, Position.OF)), random);
            if (pinch.participated()) {
                assertThat(pinch.batting().plateAppearances).isEqualTo(1);
            }
            assertThat(pinch.fielding()).isNull();

            GameResult bench = simulator.simulate(
                    GameSetup.forHitter(true, 65, 65, hitter(65, 0, HitterRole.NONE, Position.OF)), random);
            assertThat(bench.participated()).isFalse();
            assertThat(bench.batting()).isNull();
        }
    }

    @Test
    void 선발투수는_투구_수_한도_안에서_5_6이닝을_던진다() {
        SplittableRandom random = new SplittableRandom(6);
        PitchingLine total = new PitchingLine();
        int limit = GameSimulator.PITCH_LIMIT_BASE + 65 / 2;
        for (int i = 0; i < 500; i++) {
            PitchingLine line = simulator.simulate(
                    GameSetup.forPitcher(i % 2 == 0, 65, 65, pitcher(65, PitcherRole.STARTER)), random).pitching();
            assertThat(line.gamesStarted).isEqualTo(1);
            assertThat(line.pitches).isLessThanOrEqualTo(limit + 8);
            total.add(line);
        }
        double inningsPerStart = total.outs / 3.0 / 500;
        assertThat(inningsPerStart).isBetween(4.5, 7.0);
        assertThat(total.getEra()).isBetween(3.0, 6.0);
        assertThat(total.wins + total.losses).isLessThanOrEqualTo(500);
    }

    @Test
    void 마무리는_9회_이후_한_이닝만_던지고_세이브는_리드_상황에서만() {
        SplittableRandom random = new SplittableRandom(7);
        int appearances = 0;
        int saves = 0;
        for (int i = 0; i < 1000; i++) {
            GameResult r = simulator.simulate(
                    GameSetup.forPitcher(i % 2 == 0, 65, 65, pitcher(65, PitcherRole.CLOSER)), random);
            if (!r.participated()) {
                continue;
            }
            appearances++;
            PitchingLine line = r.pitching();
            assertThat(line.outs).isLessThanOrEqualTo(3);
            assertThat(line.gamesStarted).isZero();
            saves += line.saves;
            if (line.saves == 1) {
                assertThat(r.isWin()).isTrue();
            }
        }
        // 세이브 상황·동점 9회는 대략 경기의 30~55%
        assertThat(appearances / 1000.0).isBetween(0.25, 0.60);
        assertThat(saves).isPositive().isLessThanOrEqualTo(appearances);
    }

    @Test
    void 점검_등판은_점수와_상관없이_등판하고_휴식이면_등판하지_않는다() {
        SplittableRandom random = new SplittableRandom(8);
        for (int i = 0; i < 100; i++) {
            assertThat(simulator.simulate(
                    GameSetup.forPitcher(true, 65, 65, pitcher(65, PitcherRole.CLOSER_TUNE_UP)), random)
                    .participated()).isTrue();
            assertThat(simulator.simulate(
                    GameSetup.forPitcher(false, 65, 65, pitcher(65, PitcherRole.NONE)), random)
                    .participated()).isFalse();
        }
    }

    @Test
    void 포수는_상대_도루를_상대한다() {
        SplittableRandom random = new SplittableRandom(9);
        FieldingLine total = new FieldingLine();
        for (int i = 0; i < 500; i++) {
            total.add(simulator.simulate(
                    GameSetup.forHitter(true, 65, 65, hitter(80, 8, HitterRole.STARTER, Position.C)), random).fielding());
        }
        assertThat(total.stolenBasesAgainst + total.caughtStealing).isPositive();
        assertThat(total.getCaughtStealingRate()).isBetween(0.2, 0.6);
    }
}
