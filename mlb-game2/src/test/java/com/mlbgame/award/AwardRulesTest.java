package com.mlbgame.award;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.List;
import org.junit.jupiter.api.Test;

class AwardRulesTest {

    static BattingLine batting(int pa, int hits, int homeRuns) {
        BattingLine b = new BattingLine();
        b.plateAppearances = pa;
        b.atBats = pa;
        b.hits = hits;
        b.homeRuns = homeRuns;
        return b;
    }

    static PitchingLine pitching(int outs, int runs, int strikeouts, int wins, int saves) {
        PitchingLine p = new PitchingLine();
        p.outs = outs;
        p.runs = runs;
        p.strikeouts = strikeouts;
        p.wins = wins;
        p.saves = saves;
        return p;
    }

    @Test
    void 타자_점수는_OPS가_높고_타석이_많을수록_높다() {
        double great = AwardRules.hitter(batting(600, 200, 40), null);
        double average = AwardRules.hitter(batting(600, 150, 15), null);
        double partTime = AwardRules.hitter(batting(200, 67, 13), null);

        assertThat(great).isGreaterThan(average);
        assertThat(great).isGreaterThan(partTime);
        assertThat(AwardRules.hitter(null, null)).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void 투수_점수는_ERA가_낮고_이닝이_많을수록_높다() {
        double ace = AwardRules.starter(pitching(630, 50, 220, 18, 0));     // 210이닝 ERA 2.14
        double average = AwardRules.starter(pitching(540, 85, 160, 10, 0)); // 180이닝 ERA 4.25
        assertThat(ace).isGreaterThan(average);

        double closer = AwardRules.reliever(pitching(195, 15, 80, 3, 40));
        double setup = AwardRules.reliever(pitching(195, 30, 60, 3, 0));
        assertThat(closer).isGreaterThan(setup);
    }

    @Test
    void 비교_대상_전원보다_높아야_수상하고_순위는_1부터다() {
        assertThat(AwardRules.beatsAll(10, List.of(9.0, 5.0))).isTrue();
        assertThat(AwardRules.beatsAll(10, List.of(10.0))).isFalse();
        assertThat(AwardRules.beatsAll(Double.NEGATIVE_INFINITY, List.of())).isFalse();
        assertThat(AwardRules.beatsAll(1, List.of())).isTrue();
        assertThat(AwardRules.rank(10, List.of(12.0, 11.0, 3.0))).isEqualTo(3);
    }

    @Test
    void 월간_상의_제목에는_달이_들어간다() {
        assertThat(new Award(1L, 2026, 9, AwardType.PLAYER_OF_MONTH).getTitle()).isEqualTo("4월 이달의 선수");
        assertThat(new Award(1L, 2026, 54, AwardType.ROOKIE_OF_MONTH).getTitle()).isEqualTo("9월 이달의 루키");
        assertThat(new Award(1L, 2026, 54, AwardType.MVP).getTitle()).isEqualTo("2026 MVP");
        assertThat(AwardType.MVP.getPoints()).isEqualTo(5);
        assertThat(AwardType.PLAYER_OF_MONTH.getPoints()).isEqualTo(1);
    }
}
