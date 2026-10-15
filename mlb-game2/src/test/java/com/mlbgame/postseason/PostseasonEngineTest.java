package com.mlbgame.postseason;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.Team;
import com.mlbgame.league.standings.StandingsGenerator;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.player.Position;
import com.mlbgame.postseason.Bracket.Series;
import com.mlbgame.season.schedule.ScheduleGeneratorTest;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PostseasonEngineTest {

    final List<Team> teams = ScheduleGeneratorTest.teams();
    final Map<Long, Double> ratings = new HashMap<>();

    PostseasonEngineTest() {
        teams.forEach(t -> ratings.put(t.getId(), 60.0 + (t.getId() % 10)));
    }

    private Bracket bracket(long seed) {
        StandingsTable table = StandingsGenerator.table(teams, StandingsGenerator.simulate(seed, teams, ratings, 54));
        return PostseasonEngine.initial(table);
    }

    @Test
    void 와일드카드는_리그별_3대6_4대5이고_1_2번_시드는_부전승이다() {
        Bracket bracket = bracket(1L);
        assertThat(bracket.seeds).hasSize(12);
        List<Series> wildCard = bracket.round(1);
        assertThat(wildCard).hasSize(4);
        assertThat(wildCard).allMatch(s -> (s.highSeed == 3 && s.lowSeed == 6) || (s.highSeed == 4 && s.lowSeed == 5));
        assertThat(wildCard.stream().map(s -> s.league).distinct()).containsExactlyInAnyOrder("AL", "NL");
    }

    @Test
    void 시리즈는_필요한_승수에_먼저_도달한_팀이_이긴다() {
        Bracket bracket = bracket(2L);
        Series series = bracket.round(1).get(0);
        PostseasonEngine.simulate(series, 70, 60, new SplittableRandom(3));

        assertThat(Math.max(series.highWins, series.lowWins)).isEqualTo(2);
        assertThat(series.scores).hasSize(series.highWins + series.lowWins);
        assertThat(series.winnerId).isIn(series.highTeamId, series.lowTeamId);
    }

    @Test
    void 끝까지_진행하면_월드시리즈_우승팀이_나온다() {
        Bracket bracket = bracket(4L);
        SplittableRandom rng = new SplittableRandom(5);
        for (int round = 1; round <= 4; round++) {
            for (Series s : bracket.round(round)) {
                PostseasonEngine.simulate(s, ratings.get(s.highTeamId), ratings.get(s.lowTeamId), rng);
            }
            if (round < 4) {
                PostseasonEngine.createNextRound(bracket, round);
            }
        }
        assertThat(bracket.round(2)).hasSize(4);
        assertThat(bracket.round(3)).hasSize(2);
        assertThat(bracket.round(4)).hasSize(1);
        Series ws = bracket.round(4).get(0);
        assertThat(ws.league).isEqualTo("WS");
        assertThat(Math.max(ws.highWins, ws.lowWins)).isEqualTo(4);
        assertThat(bracket.champion()).isEqualTo(ws.winnerId);
        // 디비전시리즈에는 1·2번 시드가 등장한다
        assertThat(bracket.round(2)).anyMatch(s -> s.highSeed == 1).anyMatch(s -> s.highSeed == 2);
    }

    @Test
    void 탈락한_팀은_살아있지_않다() {
        Bracket bracket = bracket(6L);
        Series series = bracket.round(1).get(0);
        PostseasonEngine.simulate(series, 65, 65, new SplittableRandom(7));
        Long loser = series.winnerId.equals(series.highTeamId) ? series.lowTeamId : series.highTeamId;
        assertThat(bracket.alive(loser)).isFalse();
        assertThat(bracket.alive(series.winnerId)).isTrue();
    }

    @Test
    void 홈_경기_배정은_라운드별_패턴을_따른다() {
        assertThat(PostseasonRound.WILD_CARD.highSeedHome(2)).isTrue();
        assertThat(PostseasonRound.DIVISION.highSeedHome(2)).isFalse();
        assertThat(PostseasonRound.DIVISION.highSeedHome(4)).isTrue();
        assertThat(PostseasonRound.WORLD_SERIES.highSeedHome(4)).isFalse();
        assertThat(PostseasonRound.WORLD_SERIES.winsNeeded()).isEqualTo(4);
    }

    @Test
    void 시리즈_MVP_기준() {
        BattingLine hot = new BattingLine();
        hot.plateAppearances = 20;
        hot.atBats = 20;
        hot.hits = 10;
        hot.homeRuns = 3;
        assertThat(PostseasonService.worthyOfMvp(Position.SS, hot, new PitchingLine())).isTrue();

        PitchingLine ace = new PitchingLine();
        ace.outs = 39;
        ace.runs = 2;
        assertThat(PostseasonService.worthyOfMvp(Position.SP, new BattingLine(), ace)).isTrue();

        PitchingLine closer = new PitchingLine();
        closer.saves = 2;
        closer.runs = 1;
        assertThat(PostseasonService.worthyOfMvp(Position.CP, new BattingLine(), closer)).isFalse();
    }
}
