package com.mlbgame.simulation.balance;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.player.PitchType;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.PlateAppearanceModel;
import com.mlbgame.simulation.PlateAppearanceModelTest;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.SimulationBalance;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.GameSimulator;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.game.TeamRatings;
import com.mlbgame.simulation.rules.PitcherUsage;
import com.mlbgame.simulation.series.SeriesInput;
import com.mlbgame.simulation.series.SeriesResult;
import com.mlbgame.simulation.series.SeriesSimulator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;

/**
 * 시즌(54시리즈) 반복 시뮬레이션 도구. 밸런스 검증 테스트에서만 쓴다.
 * 상대 팀 전력은 시리즈마다 레벨 평균 ±3 에서 고르고, 부상 결장도 반영한다. 능력치는 시즌 내내 고정(훈련 없음).
 */
final class SeasonHarness {

    static final int SERIES_PER_SEASON = 54;

    private final SeriesSimulator simulator;

    SeasonHarness() {
        SimulationBalance balance = PlateAppearanceModelTest.defaultBalance();
        this.simulator = new SeriesSimulator(new GameSimulator(new PlateAppearanceModel(balance), balance));
    }

    /** 모든 능력치가 rating 인 선수 */
    static PlayerSnapshot player(Position position, int rating) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.forPosition(position)) {
            attributes.put(a, rating);
        }
        List<PitchType> repertoire = position.isPitcher()
                ? List.of(PitchType.FOUR_SEAM, PitchType.SLIDER, PitchType.CURVE, PitchType.CHANGEUP)
                : List.of();
        return new PlayerSnapshot(position, Hand.R, Hand.R, attributes, repertoire);
    }

    record Season(BattingLine batting, FieldingLine fielding, PitchingLine pitching, int injuries, int teamWins) {
    }

    Season season(PlayerSnapshot player, LeagueLevel level, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        boolean pitcher = player.position().isPitcher();
        int durability = player.attribute(Attribute.DURABILITY);
        BattingLine batting = new BattingLine();
        FieldingLine fielding = new FieldingLine();
        PitchingLine pitching = new PitchingLine();
        int fatigue = 0;
        PitcherUsage usage = PitcherUsage.seasonStart();
        int injuredSeries = 0;
        int injuries = 0;
        int wins = 0;

        for (int s = 0; s < SERIES_PER_SEASON; s++) {
            long seriesSeed = random.nextLong();
            int opponent = level.getAverageRating() + random.nextInt(7) - 3;
            if (injuredSeries > 0) {
                injuredSeries--;
                fatigue = 0;
                usage = PitcherUsage.seasonStart();
                continue;
            }
            SeriesResult r = simulator.simulate(new SeriesInput(seriesSeed, level, s % 2 == 0,
                    TeamRatings.uniform(level.getAverageRating()), TeamRatings.uniform(opponent),
                    SeriesInput.DEFAULT_GAMES, player, fatigue, usage, false));
            wins += r.wins();
            if (pitcher) {
                pitching.add(r.pitching());
            } else {
                batting.add(r.batting());
                fielding.add(r.fielding());
            }
            if (r.injury() != null) {
                injuries++;
                injuredSeries = r.injury().isSeasonEnding() ? SERIES_PER_SEASON : r.injury().seriesOut();
            }
            SeriesSimulator.CarryOver next = SeriesSimulator.carryOver(r, durability);
            fatigue = next.fatigue();
            usage = next.usage();
        }
        return new Season(batting, fielding, pitching, injuries, wins);
    }

    List<Season> seasons(PlayerSnapshot player, LeagueLevel level, int count, long baseSeed) {
        List<Season> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(season(player, level, baseSeed + i * 7919L));
        }
        return list;
    }

    // ------------------------------------------------------------ 집계

    static BattingLine totalBatting(List<Season> seasons) {
        BattingLine total = new BattingLine();
        seasons.forEach(s -> total.add(s.batting()));
        return total;
    }

    static PitchingLine totalPitching(List<Season> seasons) {
        PitchingLine total = new PitchingLine();
        seasons.forEach(s -> total.add(s.pitching()));
        return total;
    }

    static FieldingLine totalFielding(List<Season> seasons) {
        FieldingLine total = new FieldingLine();
        seasons.forEach(s -> total.add(s.fielding()));
        return total;
    }

    static double mean(List<Season> seasons, ToDoubleFunction<Season> f) {
        return seasons.stream().mapToDouble(f).average().orElse(0);
    }

    static double stdDev(List<Season> seasons, ToDoubleFunction<Season> f) {
        double m = mean(seasons, f);
        return Math.sqrt(seasons.stream().mapToDouble(s -> Math.pow(f.applyAsDouble(s) - m, 2)).average().orElse(0));
    }

    // ------------------------------------------------------------ 리포트 출력

    static void printBattingHeader() {
        System.out.printf("%-14s %6s %5s %5s %5s %5s %4s %4s %4s %5s %5s %4s%n",
                "타자", "PA", "AVG", "OBP", "SLG", "OPS", "HR", "RBI", "SB", "K%", "BB%", "부상");
    }

    static void printBatting(String label, List<Season> seasons) {
        BattingLine t = totalBatting(seasons);
        int n = seasons.size();
        System.out.printf("%-14s %6.0f %5.3f %5.3f %5.3f %5.3f %4.1f %4.1f %4.1f %4.1f%% %4.1f%% %4.2f%n",
                label, t.plateAppearances / (double) n, t.getAverage(), t.getObp(), t.getSlg(), t.getOps(),
                t.homeRuns / (double) n, t.rbi / (double) n, t.stolenBases / (double) n,
                100.0 * t.strikeouts / t.plateAppearances, 100.0 * t.walks / t.plateAppearances,
                mean(seasons, Season::injuries));
    }

    static void printPitchingHeader() {
        System.out.printf("%-14s %4s %4s %6s %5s %5s %5s %5s %5s %4s %4s %4s%n",
                "투수", "G", "GS", "IP", "ERA", "WHIP", "K/9", "BB/9", "HR/9", "W", "L", "SV");
    }

    static void printPitching(String label, List<Season> seasons) {
        PitchingLine t = totalPitching(seasons);
        double n = seasons.size();
        System.out.printf("%-14s %4.1f %4.1f %6.1f %5.2f %5.2f %5.2f %5.2f %5.2f %4.1f %4.1f %4.1f%n",
                label, t.games / n, t.gamesStarted / n, t.outs / 3.0 / n, t.getEra(), t.getWhip(),
                t.getStrikeoutsPerNine(), t.getWalksPerNine(), t.getHomeRunsPerNine(),
                t.wins / n, t.losses / n, t.saves / n);
    }
}
