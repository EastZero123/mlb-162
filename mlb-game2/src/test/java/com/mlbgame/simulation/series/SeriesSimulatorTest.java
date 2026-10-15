package com.mlbgame.simulation.series;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.player.PitchType;
import com.mlbgame.player.Position;
import com.mlbgame.season.SeriesSummary;
import com.mlbgame.simulation.PlateAppearanceModel;
import com.mlbgame.simulation.PlateAppearanceModelTest;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.SimulationBalance;
import com.mlbgame.simulation.game.GameSimulator;
import com.mlbgame.simulation.game.TeamRatings;
import com.mlbgame.simulation.rules.PitcherUsage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeriesSimulatorTest {

    final SimulationBalance balance = PlateAppearanceModelTest.defaultBalance();
    final SeriesSimulator simulator =
            new SeriesSimulator(new GameSimulator(new PlateAppearanceModel(balance), balance));
    final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static PlayerSnapshot snapshot(Position position, int rating) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.forPosition(position)) {
            attributes.put(a, rating);
        }
        List<PitchType> repertoire = position.isPitcher()
                ? List.of(PitchType.FOUR_SEAM, PitchType.SLIDER, PitchType.CURVE, PitchType.CHANGEUP)
                : List.of();
        return new PlayerSnapshot(position, Hand.R, Hand.R, attributes, repertoire);
    }

    static SeriesInput input(long seed, PlayerSnapshot player, LeagueLevel level, int fatigue, PitcherUsage usage) {
        TeamRatings average = TeamRatings.uniform(level.getAverageRating());
        return new SeriesInput(seed, level, true, average, average, SeriesInput.DEFAULT_GAMES, player, fatigue, usage,
                false);
    }

    @Test
    void 같은_입력이면_같은_시리즈가_나온다() {
        SeriesInput in = input(123L, snapshot(Position.SS, 70), LeagueLevel.MLB, 20, null);
        SeriesResult a = simulator.simulate(in);
        SeriesResult b = simulator.simulate(in);

        assertThat(a.wins()).isEqualTo(b.wins());
        assertThat(a.highlights()).isEqualTo(b.highlights());
        assertThat(a.batting().summary()).isEqualTo(b.batting().summary());
        assertThat(a.condition()).isEqualTo(b.condition());
        assertThat(a.endFatigue()).isEqualTo(b.endFatigue());
    }

    @Test
    void 시드가_다르면_결과가_달라진다() {
        PlayerSnapshot player = snapshot(Position.SS, 70);
        long distinct = java.util.stream.LongStream.range(0, 20)
                .mapToObj(seed -> simulator.simulate(input(seed, player, LeagueLevel.MLB, 0, null)))
                .map(r -> r.games().stream().map(g -> g.teamScore() + ":" + g.opponentScore()).toList())
                .distinct()
                .count();
        assertThat(distinct).isGreaterThan(15);
    }

    @Test
    void 시리즈는_3경기이고_승패_합이_맞다() {
        SeriesResult r = simulator.simulate(input(1L, snapshot(Position.OF, 65), LeagueLevel.AA, 0, null));
        assertThat(r.games()).hasSize(3);
        assertThat(r.wins() + r.losses()).isEqualTo(3);
        assertThat(r.batting()).isNotNull();
        assertThat(r.pitching()).isNull();
    }

    @Test
    void 주전_타자는_3경기_선발하고_피로가_쌓인다() {
        SeriesResult r = simulator.simulate(input(2L, snapshot(Position.B2, 70), LeagueLevel.MLB, 10, null));
        if (r.injury() == null) {
            assertThat(r.batting().games).isEqualTo(3);
        }
        assertThat(r.endFatigue()).isGreaterThan(10);
    }

    @Test
    void 선발투수는_시즌_첫_시리즈에서_한_번_선발한다() {
        SeriesResult r = simulator.simulate(
                input(3L, snapshot(Position.SP, 65), LeagueLevel.MLB, 0, PitcherUsage.seasonStart()));
        assertThat(r.pitching().gamesStarted).isEqualTo(1);
        assertThat(r.endUsage().gamesSinceStart()).isBetween(1, 3);
    }

    @Test
    void 시리즈를_이어가면_선발은_5경기마다_등판한다() {
        PlayerSnapshot sp = snapshot(Position.SP, 65);
        int durability = sp.attribute(Attribute.DURABILITY);
        int fatigue = 0;
        PitcherUsage usage = PitcherUsage.seasonStart();
        int starts = 0;
        for (int s = 0; s < 10; s++) {      // 30경기
            SeriesResult r = simulator.simulate(input(100 + s, sp, LeagueLevel.MLB, fatigue, usage));
            if (r.injury() != null) {
                return;                      // 부상이 나면 이 테스트는 의미가 없으므로 종료
            }
            starts += r.pitching().gamesStarted;
            SeriesSimulator.CarryOver next = SeriesSimulator.carryOver(r, durability);
            fatigue = next.fatigue();
            usage = next.usage();
        }
        assertThat(starts).isEqualTo(6);     // 30경기 / 5 = 6 (이동일은 로테이션에 세지 않음)
    }

    @Test
    void 부상을_당하면_이후_경기는_결장한다() {
        // 내구성 1, 피로 100 → 부상 확률을 크게 올려 부상 시드를 찾는다
        Map<Attribute, Integer> attributes = new EnumMap<>(snapshot(Position.SS, 65).attributes());
        attributes.put(Attribute.DURABILITY, 1);
        PlayerSnapshot fragile = new PlayerSnapshot(Position.SS, Hand.R, Hand.R, attributes, List.of());
        for (long seed = 0; seed < 5000; seed++) {
            SeriesResult r = simulator.simulate(input(seed, fragile, LeagueLevel.MLB, 100, null));
            if (r.injury() == null) {
                continue;
            }
            int injuredGame = -1;
            for (int i = 0; i < r.highlights().size(); i++) {
                if (r.highlights().get(i).contains("부상 발생")) {
                    injuredGame = Integer.parseInt(r.highlights().get(i).substring(0, 1));
                }
            }
            for (int g = injuredGame; g < 3; g++) {
                assertThat(r.games().get(g).participated()).as("seed " + seed + " game " + (g + 1)).isFalse();
            }
            return;
        }
        throw new AssertionError("부상 시드를 찾지 못함");
    }

    @Test
    void 입력을_JSON으로_저장했다가_읽어도_같은_결과가_재현된다() throws Exception {
        SeriesInput original = input(777L, snapshot(Position.CP, 72), LeagueLevel.AAA, 35,
                new PitcherUsage(10, 1, 18, 1));
        String json = mapper.writeValueAsString(original);
        SeriesInput restored = mapper.readValue(json, SeriesInput.class);

        assertThat(restored).isEqualTo(original);
        SeriesResult first = simulator.simulate(original);
        SeriesResult replay = simulator.simulate(restored);
        assertThat(replay.highlights()).isEqualTo(first.highlights());
        assertThat(replay.pitching().summary()).isEqualTo(first.pitching().summary());
    }

    @Test
    void 요약을_JSON으로_저장하고_읽을_수_있다() throws Exception {
        SeriesResult r = simulator.simulate(input(5L, snapshot(Position.SS, 68), LeagueLevel.MLB, 0, null));
        String json = mapper.writeValueAsString(SeriesSummary.of(r));
        SeriesSummary summary = mapper.readValue(json, SeriesSummary.class);

        assertThat(json.length()).isLessThan(4000);   // series_records.summary_json VARCHAR(4000)
        assertThat(summary.wins()).isEqualTo(r.wins());
        assertThat(summary.batting().hits).isEqualTo(r.batting().hits);
        assertThat(summary.condition()).isEqualTo(r.condition());
    }
}
