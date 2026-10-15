package com.mlbgame.season.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Position;
import com.mlbgame.season.promotion.PromotionRules.Decision;
import com.mlbgame.season.promotion.PromotionRules.Verdict;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import org.junit.jupiter.api.Test;

class PromotionRulesTest {

    /** OPS 를 원하는 값으로 만든 타격 기록 (100타석, 볼넷 없음, 단타만 → OPS = 2 × 타율) */
    static BattingLine batting(int plateAppearances, double ops) {
        BattingLine line = new BattingLine();
        line.plateAppearances = plateAppearances;
        line.atBats = plateAppearances;
        line.hits = (int) Math.round(plateAppearances * ops / 2);
        return line;
    }

    static PitchingLine pitching(int outs, int runs) {
        PitchingLine line = new PitchingLine();
        line.outs = outs;
        line.runs = runs;
        return line;
    }

    @Test
    void 판정은_9시리즈마다_시즌_마지막_전까지만() {
        assertThat(PromotionRules.isEvaluationPoint(9, 54)).isTrue();
        assertThat(PromotionRules.isEvaluationPoint(45, 54)).isTrue();
        assertThat(PromotionRules.isEvaluationPoint(54, 54)).isFalse();
        assertThat(PromotionRules.isEvaluationPoint(10, 54)).isFalse();
    }

    @Test
    void 레벨_평균보다_OVR이_4_이상_높으면_성적이_평범해도_승격한다() {
        Decision d = PromotionRules.evaluate(LeagueLevel.A, 49, Position.SS, batting(100, 0.720), null);
        assertThat(d.verdict()).isEqualTo(Verdict.PROMOTE);
        assertThat(d.to()).isEqualTo(LeagueLevel.AA);
    }

    @Test
    void OVR이_조금_모자라도_성적이_좋으면_승격한다() {
        // OVR 차이 −2, OPS .880 → 성적 +3.2 → 점수 −2 + 6.4 = +4.4
        Decision d = PromotionRules.evaluate(LeagueLevel.AA, 50, Position.OF, batting(100, 0.880), null);
        assertThat(d.performance()).isCloseTo(3.2, within(0.05));
        assertThat(d.verdict()).isEqualTo(Verdict.PROMOTE);
    }

    @Test
    void OVR이_높아도_성적이_나쁘면_잔류할_수_있다() {
        // OVR 차이 +4, OPS .570 → 성적 −3 → 점수 4 − 6 = −2
        Decision d = PromotionRules.evaluate(LeagueLevel.AA, 56, Position.OF, batting(100, 0.570), null);
        assertThat(d.verdict()).isEqualTo(Verdict.STAY);
    }

    @Test
    void 표본이_부족하면_성적은_반영하지_않는다() {
        Decision d = PromotionRules.evaluate(LeagueLevel.AA, 50, Position.OF, batting(20, 1.200), null);
        assertThat(d.performance()).isZero();
        assertThat(d.verdict()).isEqualTo(Verdict.STAY);
    }

    @Test
    void 성적_점수는_4로_제한된다() {
        Decision d = PromotionRules.evaluate(LeagueLevel.AA, 52, Position.OF, batting(100, 2.000), null);
        assertThat(d.performance()).isEqualTo(4.0);
    }

    @Test
    void 메이저에서_크게_밀리면_트리플A로_강등된다() {
        Decision d = PromotionRules.evaluate(LeagueLevel.MLB, 58, Position.SS, batting(100, 0.600), null);
        assertThat(d.verdict()).isEqualTo(Verdict.DEMOTE);
        assertThat(d.to()).isEqualTo(LeagueLevel.AAA);
    }

    @Test
    void 메이저는_승격이_없고_루키는_강등이_없다() {
        assertThat(PromotionRules.evaluate(LeagueLevel.MLB, 90, Position.SS, null, null).verdict()).isEqualTo(Verdict.STAY);
        assertThat(PromotionRules.evaluate(LeagueLevel.ROOKIE, 10, Position.SS, null, null).verdict()).isEqualTo(Verdict.STAY);
    }

    @Test
    void 투수는_ERA로_성적을_본다() {
        // 선발 18이닝 2실점 → ERA 1.00 → (4.30 − 1.00) / 0.60 = 5.5 → 제한 4
        assertThat(PromotionRules.performance(Position.SP, null, pitching(54, 2))).isEqualTo(4.0);
        // 선발 10이닝은 표본 부족
        assertThat(PromotionRules.performance(Position.SP, null, pitching(30, 0))).isZero();
        // 마무리 6이닝 4실점 → ERA 6.00 → (4.00 − 6.00) / 0.80 = −2.5
        assertThat(PromotionRules.performance(Position.CP, null, pitching(18, 4))).isCloseTo(-2.5, within(1e-9));
    }
}
