package com.mlbgame.league.mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mlbgame.award.AwardType;
import com.mlbgame.combine.DraftRules;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.Team;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.world.PayrollRules;
import com.mlbgame.player.Education;
import com.mlbgame.postseason.Bracket;
import com.mlbgame.postseason.Bracket.Series;
import com.mlbgame.postseason.KboPostseasonFormat;
import com.mlbgame.postseason.PostseasonEngine;
import com.mlbgame.postseason.PostseasonFormat;
import com.mlbgame.season.schedule.ScheduledSeries;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** K3: KBO 규칙셋 (docs/kbo-mode.md 3장) */
class KboRulesetTest {

    private final LeagueRuleset kbo = LeagueRulesets.of(LeagueMode.KBO);

    static List<Team> kboTeams() {
        return LongStream.rangeClosed(31, 40).mapToObj(id -> {
            Team team = mock(Team.class);
            when(team.getId()).thenReturn(id);
            when(team.getLeague()).thenReturn("KBO");
            when(team.getLeagueMode()).thenReturn(LeagueMode.KBO);
            return team;
        }).toList();
    }

    @Test
    void 레벨은_퓨처스와_1군() {
        assertThat(kbo.levels()).containsExactly(LeagueLevel.FUTURES, LeagueLevel.KBO);
        assertThat(kbo.topLevel()).isEqualTo(LeagueLevel.KBO);
        assertThat(kbo.above(LeagueLevel.FUTURES)).isEqualTo(LeagueLevel.KBO);
        assertThat(kbo.below(LeagueLevel.FUTURES)).isNull();
        assertThat(kbo.tradeEligible(LeagueLevel.FUTURES)).isTrue();
        assertThat(LeagueRulesets.of(LeagueLevel.FUTURES)).isSameAs(kbo);
    }

    @Test
    void 일정은_54시리즈_144경기_상대마다_16경기_홈원정_8대8() {
        List<Team> teams = kboTeams();
        Team me = teams.get(0);
        List<ScheduledSeries> schedule = kbo.schedule(7L, me, teams);

        assertThat(schedule).hasSize(54);
        assertThat(kbo.seasonGames()).isEqualTo(144);
        assertThat(schedule.stream().mapToInt(ScheduledSeries::games).sum()).isEqualTo(144);
        assertThat(schedule).allSatisfy(s -> assertThat(s.games()).isEqualTo(kbo.gamesInSeries(s.seriesNo())));
        assertThat(schedule).noneMatch(s -> s.opponentId().equals(me.getId()));

        Map<Long, int[]> byOpponent = new HashMap<>();          // [전체 경기, 홈 경기]
        for (ScheduledSeries s : schedule) {
            int[] g = byOpponent.computeIfAbsent(s.opponentId(), id -> new int[2]);
            g[0] += s.games();
            if (s.home()) g[1] += s.games();
        }
        assertThat(byOpponent).hasSize(9);
        assertThat(byOpponent.values()).allSatisfy(g -> {
            assertThat(g[0]).isEqualTo(16);
            assertThat(g[1]).isEqualTo(8);
        });
        assertThat(kbo.schedule(7L, me, teams)).isEqualTo(schedule);   // 같은 시드면 같은 일정
    }

    @Test
    void 한_달_경기_수는_3연전_27경기_2연전_18경기() {
        assertThat(kbo.gamesInMonth(1)).isEqualTo(27);
        assertThat(kbo.gamesInMonth(4)).isEqualTo(27);
        assertThat(kbo.gamesInMonth(5)).isEqualTo(18);
        assertThat(kbo.gamesInMonth(6)).isEqualTo(18);
    }

    @Test
    void 연봉은_원화로_표시하고_FA는_고졸8년_대졸7년() {
        PayrollRules payroll = kbo.payroll();
        assertThat(payroll.format(150_000_000)).isEqualTo("1억 5,000만원");
        assertThat(payroll.format(1_000_000_000)).isEqualTo("10억원");
        assertThat(payroll.format(30_000_000)).isEqualTo("3,000만원");
        assertThat(payroll.minimumSalary()).isEqualTo(30_000_000L);
        assertThat(payroll.levelSalary(LeagueLevel.FUTURES)).isEqualTo(30_000_000L);
        assertThat(payroll.levelSalary(LeagueLevel.KBO)).isEqualTo(30_000_000L);
        assertThat(payroll.marketValue(65)).isEqualTo(200_000_000L);
        // K4: 스타 격차는 MLB 보다 크다 — OVR 90 이면 연 20~30억 (실제 최상위 FA·비FA 다년 계약 수준)
        assertThat(payroll.marketValue(90)).isBetween(2_000_000_000L, 3_000_000_000L);
        assertThat(payroll.freeAgencyService(false)).isEqualTo(8);
        assertThat(payroll.freeAgencyService(true)).isEqualTo(7);
        assertThat(payroll.salaryByService(1, 75)).isGreaterThan(payroll.minimumSalary());
        assertThat(payroll.salaryByService(7, 75)).isLessThan(payroll.marketValue(75));
    }

    @Test
    void 드래프트는_10개_구단_11라운드_전원_퓨처스() {
        DraftRules draft = kbo.draft();
        assertThat(draft.round(1)).isEqualTo(1);          // S
        assertThat(draft.round(3)).isEqualTo(2);          // A
        assertThat(draft.round(8)).isEqualTo(5);          // B
        assertThat(draft.round(20)).isEqualTo(11);        // D 마지막
        assertThat(draft.signingBonus(1)).isEqualTo(500_000_000L);
        assertThat(draft.signingBonus(110)).isEqualTo(30_000_000L);
        assertThat(draft.startLevel(Education.COLLEGE, 1)).isEqualTo(LeagueLevel.FUTURES);
        assertThat(DraftRules.MLB.round(13)).isEqualTo(13);   // MLB 는 그대로
    }

    @Test
    void 승강_판정은_3시리즈마다() {
        assertThat(kbo.promotion().interval()).isEqualTo(3);
        assertThat(com.mlbgame.season.promotion.PromotionRules.isEvaluationPoint(kbo.promotion(), 3, 54)).isTrue();
        assertThat(com.mlbgame.season.promotion.PromotionRules.isEvaluationPoint(kbo.promotion(), 4, 54)).isFalse();
    }

    @Test
    void 포스트시즌은_5팀_계단식이고_4위는_1승_안고_시작한다() {
        List<Team> teams = kboTeams();
        List<StandingsTable.Row> rows = java.util.stream.IntStream.range(0, teams.size())
                .mapToObj(i -> new StandingsTable.Row(teams.get(i), 90 - i * 3, 54 + i * 3))
                .toList();
        PostseasonFormat format = kbo.postseason();
        Bracket bracket = format.initial(new StandingsTable(rows));

        assertThat(bracket.seeds).hasSize(5);
        Series wildCard = bracket.round(1).get(0);
        assertThat(wildCard.highSeed).isEqualTo(4);
        assertThat(wildCard.lowSeed).isEqualTo(5);
        assertThat(wildCard.highWins).isEqualTo(1);

        SplittableRandom rng = new SplittableRandom(3);
        for (int round = 1; round <= format.finalRound(); round++) {
            for (Series s : bracket.round(round)) {
                PostseasonEngine.simulate(s, format.round(round), 65, 65, rng);
                assertThat(Math.max(s.highWins, s.lowWins)).isEqualTo(format.round(round).winsNeeded());
            }
            if (round < format.finalRound()) {
                format.createNextRound(bracket, round);
            }
        }
        assertThat(bracket.round(2).get(0).highSeed).isEqualTo(3);
        assertThat(bracket.round(3).get(0).highSeed).isEqualTo(2);
        assertThat(bracket.round(4).get(0).highSeed).isEqualTo(1);
        assertThat(bracket.champion(format.finalRound())).isNotNull();
        assertThat(bracket.round(1).get(0).scores.size()).isLessThanOrEqualTo(2);   // 와일드카드 최대 2경기

        assertThat(format.round(4).getLabel()).isEqualTo("한국시리즈");
        assertThat(format.seriesMvp(3).type()).isEqualTo(AwardType.KBO_PO_MVP);
        assertThat(format.seriesMvp(4).type()).isEqualTo(AwardType.KBO_KS_MVP);
        assertThat(format.seriesMvp(2)).isNull();
    }

    @Test
    void 시상과_용어는_KBO_것() {
        assertThat(kbo.awards().mvp()).isEqualTo(AwardType.KBO_MVP);
        assertThat(kbo.awards().bestStarter()).isEqualTo(AwardType.KBO_GOLDEN_GLOVE);
        assertThat(kbo.awards().rookieOfMonth()).isNull();
        assertThat(kbo.awards().titles()).isTrue();
        assertThat(kbo.terms().finals()).isEqualTo("한국시리즈");
        assertThat(kbo.terms().top()).isEqualTo("1군");
        assertThat(kbo.leagues()).containsExactly("KBO");
        assertThat(kbo.divisions()).isEmpty();
    }

    @Test
    void K4_명예의_전당_누적_기록은_144경기만큼_보정한다() {
        assertThat(kbo.hallOfFameStatScale()).isCloseTo(1.125, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(LeagueRulesets.MLB.hallOfFameStatScale()).isEqualTo(1.0);
    }

    @Test
    void K4_퓨처스는_육성_리그라_신인도_출전한다() {
        assertThat(LeagueLevel.FUTURES.getRegularRating()).isLessThan(LeagueLevel.FUTURES.getAverageRating());
        assertThat(com.mlbgame.simulation.rules.LineupRules.standing(30, LeagueLevel.FUTURES))
                .isEqualTo(com.mlbgame.simulation.rules.LineupRules.Standing.PLATOON);   // 고졸 신인
        assertThat(com.mlbgame.simulation.rules.LineupRules.standing(40, LeagueLevel.FUTURES))
                .isEqualTo(com.mlbgame.simulation.rules.LineupRules.Standing.REGULAR);   // 대졸 신인
        // MLB 레벨은 그대로 (주전 기준 = 레벨 평균)
        for (LeagueLevel level : LeagueRulesets.MLB.levels()) {
            assertThat(level.getRegularRating()).isEqualTo(level.getAverageRating());
        }
    }

    @Test
    void K4_시상_점수_기준은_KBO_리그_평균() {
        com.mlbgame.simulation.game.BattingLine line = new com.mlbgame.simulation.game.BattingLine();
        line.plateAppearances = 100;
        line.atBats = 100;
        line.hits = 37;                                     // OPS .740 = MLB 기준 위, KBO 기준 아래
        assertThat(com.mlbgame.award.AwardRules.Scoring.MLB.hitter(line, null)).isPositive();
        assertThat(kbo.awards().scoring().hitter(line, null)).isNegative();
        assertThat(kbo.awards().pitcherMvp()).isTrue();
        assertThat(LeagueRulesets.MLB.awards().pitcherMvp()).isFalse();
    }

    @Test
    void 개인_타이틀_목록() {
        Map<String, Long> titles = java.util.Arrays.stream(AwardType.values())
                .filter(t -> t.name().startsWith("KBO_TITLE_"))
                .collect(Collectors.groupingBy(AwardType::name, Collectors.counting()));
        assertThat(titles).hasSize(8);
    }
}
