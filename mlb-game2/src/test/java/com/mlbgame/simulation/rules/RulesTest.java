package com.mlbgame.simulation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.UserHitter.HitterRole;
import com.mlbgame.simulation.game.UserPitcher.PitcherRole;
import com.mlbgame.simulation.rules.LineupRules.Standing;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RulesTest {

    /** 기본값 + 배분 → 원하는 능력치 성향의 타자 스냅샷 */
    static PlayerSnapshot hitter(Position position, int base, Map<Attribute, Integer> allocation) {
        return PlayerSnapshot.of(Player.create(1L, "테스트", 1, position, Hand.R, Hand.R, Nationality.USA,
                Education.HIGH_SCHOOL, SchoolRegion.US_EAST, base, allocation, List.of()));
    }

    // ------------------------------------------------------------ 출전 판정

    @Test
    void 레벨_평균_대비_OVR로_입지가_정해진다() {
        assertThat(LineupRules.standing(66, LeagueLevel.MLB)).isEqualTo(Standing.REGULAR);
        assertThat(LineupRules.standing(60, LeagueLevel.MLB)).isEqualTo(Standing.PLATOON);
        assertThat(LineupRules.standing(58, LeagueLevel.MLB)).isEqualTo(Standing.BENCH);
        assertThat(LineupRules.standing(58, LeagueLevel.AAA)).isEqualTo(Standing.REGULAR);
    }

    @Test
    void 주전은_매경기_선발이고_많이_지치면_쉰다() {
        SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < 100; i++) {
            assertThat(LineupRules.roleForGame(70, LeagueLevel.MLB, 30, random)).isEqualTo(HitterRole.STARTER);
            assertThat(LineupRules.roleForGame(70, LeagueLevel.MLB, 85, random)).isNotEqualTo(HitterRole.STARTER);
        }
    }

    @Test
    void 플래툰과_벤치는_선발_비율이_낮다() {
        SplittableRandom random = new SplittableRandom(2);
        int platoonStarts = 0;
        int benchStarts = 0;
        int n = 10_000;
        for (int i = 0; i < n; i++) {
            if (LineupRules.roleForGame(62, LeagueLevel.MLB, 0, random) == HitterRole.STARTER) platoonStarts++;
            if (LineupRules.roleForGame(50, LeagueLevel.MLB, 0, random) == HitterRole.STARTER) benchStarts++;
        }
        assertThat(platoonStarts / (double) n).isBetween(0.52, 0.62);   // 75% − 3×6% = 57%
        assertThat(benchStarts / (double) n).isBetween(0.12, 0.18);
    }

    @Test
    void 능력치_성향에_따라_타순이_정해진다() {
        // MLB 평균 65 보다 8 이상 높은 주전 (기본 70 + 배분)
        PlayerSnapshot slugger = hitter(Position.B1, 70, Map.of(Attribute.POWER, 25, Attribute.GAP_POWER, 25));
        PlayerSnapshot speedster = hitter(Position.OF, 70, Map.of(Attribute.SPEED, 25, Attribute.STEALING, 25, Attribute.EYE, 5));
        PlayerSnapshot contactHitter = hitter(Position.B2, 70, Map.of(Attribute.CONTACT, 25, Attribute.AVOID_K, 25));

        assertThat(LineupRules.battingSlot(slugger, LeagueLevel.MLB)).isEqualTo(4);
        assertThat(LineupRules.battingSlot(speedster, LeagueLevel.MLB)).isEqualTo(1);
        assertThat(LineupRules.battingSlot(contactHitter, LeagueLevel.MLB)).isEqualTo(3);
    }

    @Test
    void 입지가_낮으면_하위_타순이고_포수는_한_자리_아래다() {
        PlayerSnapshot platoon = hitter(Position.SS, 61, Map.of());
        PlayerSnapshot bench = hitter(Position.SS, 50, Map.of());
        PlayerSnapshot catcher = hitter(Position.C, 61, Map.of());

        assertThat(LineupRules.battingSlot(platoon, LeagueLevel.MLB)).isEqualTo(7);
        assertThat(LineupRules.battingSlot(bench, LeagueLevel.MLB)).isEqualTo(8);
        assertThat(LineupRules.battingSlot(catcher, LeagueLevel.MLB)).isEqualTo(8);
    }

    // ------------------------------------------------------------ 투수 운용

    @Test
    void 선발은_5경기마다_등판하고_회복력이_낮으면_하루_밀린다() {
        assertThat(countStarts(65, 30)).isEqualTo(6);   // 30경기 / 5 = 6회
        assertThat(countStarts(30, 30)).isEqualTo(5);   // 회복력 낮음 → 6경기 간격
    }

    private int countStarts(int recovery, int games) {
        PitcherUsage usage = PitcherUsage.seasonStart();
        int starts = 0;
        for (int i = 0; i < games; i++) {
            boolean start = usage.roleForGame(true, recovery, 65, LeagueLevel.MLB) == PitcherRole.STARTER;
            if (start) starts++;
            usage = usage.afterGame(start, start, start ? 90 : 0);
        }
        return starts;
    }

    @Test
    void 마무리는_3일_연속_등판할_수_없다() {
        PitcherUsage usage = PitcherUsage.seasonStart()
                .afterGame(true, false, 15)
                .afterGame(true, false, 15);
        assertThat(usage.roleForGame(false, 65, 65, LeagueLevel.MLB)).isEqualTo(PitcherRole.NONE);
        assertThat(PitcherUsage.restAfterSeries(usage).roleForGame(false, 65, 65, LeagueLevel.MLB))
                .isEqualTo(PitcherRole.CLOSER);
    }

    @Test
    void 전날_많이_던진_마무리는_쉬고_회복력이_높으면_한도가_올라간다() {
        PitcherUsage heavy = PitcherUsage.seasonStart().afterGame(true, false, 35);
        assertThat(heavy.roleForGame(false, 40, 65, LeagueLevel.MLB)).isEqualTo(PitcherRole.NONE);   // 한도 33
        assertThat(heavy.roleForGame(false, 80, 65, LeagueLevel.MLB)).isEqualTo(PitcherRole.CLOSER); // 한도 41
    }

    @Test
    void 오래_쉰_마무리는_점검_등판하고_연투하면_구위가_떨어진다() {
        PitcherUsage rested = new PitcherUsage(10, 0, 0, 4);
        assertThat(rested.roleForGame(false, 65, 65, LeagueLevel.MLB)).isEqualTo(PitcherRole.CLOSER_TUNE_UP);

        PitcherUsage backToBack = new PitcherUsage(10, 1, 15, 1);
        assertThat(backToBack.closerFatigueOffset(65)).isLessThan(0);
        assertThat(backToBack.closerFatigueOffset(90)).isGreaterThan(backToBack.closerFatigueOffset(40));
    }

    // ------------------------------------------------------------ 피로·부상

    @Test
    void 피로도_70_초과면_경기력이_떨어지고_내구성이_높으면_덜_지친다() {
        assertThat(Fatigue.formOffset(Condition.NORMAL, 71)).isEqualTo(-5);
        assertThat(Fatigue.formOffset(Condition.BEST, 30)).isEqualTo(4);
        assertThat(Fatigue.gain(10, 90)).isLessThan(Fatigue.gain(10, 40));
        assertThat(Fatigue.recover(50, 10, 90)).isLessThan(Fatigue.recover(50, 10, 40));
        assertThat(Fatigue.add(95, 20)).isEqualTo(100);
    }

    @Test
    void 내구성이_높을수록_피로가_낮을수록_부상_확률이_낮다() {
        double base = InjuryRules.chance(InjuryRules.HITTER_START, 65, 0);
        assertThat(base).isCloseTo(0.0025, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(InjuryRules.chance(InjuryRules.HITTER_START, 90, 0)).isLessThan(base);
        assertThat(InjuryRules.chance(InjuryRules.HITTER_START, 65, 80)).isGreaterThan(base * 1.8);
    }

    @Test
    void 시즌_부상_빈도가_MLB_수준이다() {
        SplittableRandom random = new SplittableRandom(3);
        int seasons = 2000;
        int injuries = 0;
        for (int s = 0; s < seasons; s++) {
            for (int g = 0; g < 150; g++) {
                if (InjuryRules.rollForHitter(HitterRole.STARTER, 65, 30, random).isPresent()) injuries++;
            }
        }
        assertThat(injuries / (double) seasons).isBetween(0.28, 0.48);   // 시즌당 약 0.37회
    }

    @Test
    void 결장하면_부상_판정을_하지_않는다() {
        SplittableRandom random = new SplittableRandom(4);
        for (int i = 0; i < 1000; i++) {
            assertThat(InjuryRules.rollForHitter(HitterRole.NONE, 1, 100, random)).isEmpty();
            assertThat(InjuryRules.rollForPitcher(PitcherRole.NONE, 1, 100, random)).isEmpty();
        }
    }

    @Test
    void 부상_심각도와_결장_기간() {
        SplittableRandom random = new SplittableRandom(5);
        for (int i = 0; i < 500; i++) {
            InjurySeverity severity = InjurySeverity.roll(random);
            int out = severity.rollSeriesOut(random);
            switch (severity) {
                case MINOR -> assertThat(out).isBetween(1, 3);
                case MAJOR -> assertThat(out).isBetween(5, 20);
                case SEASON_ENDING -> assertThat(out).isEqualTo(Integer.MAX_VALUE);
            }
        }
    }
}
