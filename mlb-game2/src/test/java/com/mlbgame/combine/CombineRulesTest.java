package com.mlbgame.combine;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Education;
import com.mlbgame.player.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CombineRulesTest {

    @ParameterizedTest
    @CsvSource({
            "60.0, S, 1",
            "52.0, S, 1",
            "51.9, A, 2",
            "47.0, A, 3",
            "45.0, B, 5",
            "41.0, B, 8",
            "38.0, C, 12",
            "36.0, C, 14",
            "35.9, D, 15",
            "20.0, D, 20"
    })
    void 종합_점수로_등급과_라운드가_정해진다(double score, CombineGrade grade, int round) {
        assertThat(CombineGrade.of(score)).isEqualTo(grade);
        assertThat(grade.roundFor(score)).isEqualTo(round);
    }

    @Test
    void 라운드는_항상_등급_범위_안에_있다() {
        for (double score = 0; score <= 70; score += 0.1) {
            int round = CombineGrade.of(score).roundFor(score);
            assertThat(round).isBetween(1, 20);
        }
    }

    @Test
    void 계약금은_순번이_늦을수록_줄고_최저액이_있다() {
        assertThat(DraftService.signingBonus(1)).isEqualTo(9_500_000);
        assertThat(DraftService.signingBonus(30)).isLessThan(DraftService.signingBonus(1));
        assertThat(DraftService.signingBonus(600)).isEqualTo(150_000);
    }

    @Test
    void 대졸_상위_라운드만_싱글A에서_시작한다() {
        assertThat(DraftService.startLevel(Education.COLLEGE, 10)).isEqualTo(LeagueLevel.A);
        assertThat(DraftService.startLevel(Education.COLLEGE, 11)).isEqualTo(LeagueLevel.ROOKIE);
        assertThat(DraftService.startLevel(Education.HIGH_SCHOOL, 1)).isEqualTo(LeagueLevel.ROOKIE);
    }

    @Test
    void 포지션별로_컴바인_종목이_4개다() {
        assertThat(CombineTest.forPosition(Position.SS)).containsExactly(
                CombineTest.DASH_60, CombineTest.BATTING_PRACTICE, CombineTest.FIELDING_DRILL, CombineTest.THROWING);
        assertThat(CombineTest.forPosition(Position.C)).contains(CombineTest.POP_TIME);
        assertThat(CombineTest.forPosition(Position.CP)).containsExactly(
                CombineTest.VELOCITY, CombineTest.CONTROL, CombineTest.BULLPEN, CombineTest.STAMINA_TEST);
    }

    @Test
    void 점수에서_실측_기록으로_환산한다() {
        assertThat(CombineTest.VELOCITY.recordOf(1)).isEqualTo(130.0);
        assertThat(CombineTest.VELOCITY.recordOf(100)).isEqualTo(165.0);
        assertThat(CombineTest.DASH_60.recordOf(30)).isEqualTo(7.21);
        assertThat(CombineTest.CONTROL.recordOf(30)).isEqualTo(10.0);
    }
}
