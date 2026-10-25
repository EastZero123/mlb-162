package com.mlbgame.league.mode;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.award.AwardType;
import com.mlbgame.combine.DraftRules;
import com.mlbgame.contract.ContractRules;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.world.Market;
import com.mlbgame.league.world.Payroll;
import com.mlbgame.player.Education;
import com.mlbgame.player.Position;
import com.mlbgame.postseason.PostseasonFormat;
import com.mlbgame.postseason.PostseasonRound;
import com.mlbgame.season.promotion.PromotionRules;
import com.mlbgame.season.promotion.PromotionRules.Decision;
import com.mlbgame.simulation.game.BattingLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** K1 규칙셋 분리: MLB 규칙셋이 분리 전 상수·규칙과 똑같이 동작하는지 (docs/kbo-mode.md) */
class MlbRulesetTest {

    private final LeagueRuleset mlb = LeagueRulesets.of(LeagueMode.MLB);

    @Test
    void 레벨_순서와_최상위() {
        assertThat(mlb.levels()).containsExactly(LeagueLevel.ROOKIE, LeagueLevel.A, LeagueLevel.AA, LeagueLevel.AAA,
                LeagueLevel.MLB);
        assertThat(mlb.topLevel()).isEqualTo(LeagueLevel.MLB);
        assertThat(LeagueLevel.MLB.isTop()).isTrue();
        assertThat(LeagueLevel.AAA.isTop()).isFalse();
        assertThat(mlb.above(LeagueLevel.AAA)).isEqualTo(LeagueLevel.MLB);
        assertThat(mlb.above(LeagueLevel.MLB)).isNull();
        assertThat(mlb.below(LeagueLevel.A)).isEqualTo(LeagueLevel.ROOKIE);
        assertThat(mlb.below(LeagueLevel.ROOKIE)).isNull();
        assertThat(LeagueRulesets.of(LeagueLevel.AA)).isSameAs(mlb);
    }

    @Test
    void 시즌은_54시리즈_162경기_한달_27경기() {
        assertThat(mlb.seriesPerSeason()).isEqualTo(54);
        assertThat(mlb.seasonGames()).isEqualTo(162);
        assertThat(mlb.seriesPerMonth()).isEqualTo(9);
        assertThat(mlb.gamesInMonth(1)).isEqualTo(27);
        assertThat(mlb.gamesInMonth(6)).isEqualTo(27);
        assertThat(mlb.gamesBetween(1, mlb.allStarAfterSeries())).isEqualTo(81);
    }

    @Test
    void 트레이드는_메이저와_트리플A만() {
        assertThat(mlb.tradeEligible(LeagueLevel.MLB)).isTrue();
        assertThat(mlb.tradeEligible(LeagueLevel.AAA)).isTrue();
        assertThat(mlb.tradeEligible(LeagueLevel.AA)).isFalse();
    }

    @Test
    void 연봉_규칙은_기존_Payroll_과_같다() {
        var payroll = mlb.payroll();
        assertThat(payroll.minimumSalary()).isEqualTo(760_000L);
        assertThat(payroll.luxuryTaxThreshold()).isEqualTo(240_000_000L);
        for (double ovr : List.of(40.0, 65.0, 72.5, 90.0, 100.0)) {
            assertThat(payroll.marketValue(ovr)).isEqualTo(Payroll.marketValue(ovr));
            for (int service = 0; service <= 7; service++) {
                assertThat(payroll.salaryByService(service, ovr)).isEqualTo(Payroll.salaryByService(service, ovr));
            }
        }
        assertThat(payroll.levelSalary(LeagueLevel.ROOKIE)).isEqualTo(20_000L);
        assertThat(payroll.levelSalary(LeagueLevel.AAA)).isEqualTo(40_000L);
        assertThat(payroll.levelSalary(LeagueLevel.MLB)).isEqualTo(760_000L);
        assertThat(payroll.baseBudget(Market.BIG)).isEqualTo(230_000_000L);
        assertThat(payroll.format(1_234_567)).isEqualTo("$1,234,567");
        assertThat(ContractRules.fairArbitration(payroll, 4, 75)).isEqualTo(ContractRules.fairArbitration(4, 75));
        assertThat(mlb.seriesPerServiceYear()).isEqualTo(ContractRules.SERIES_PER_SERVICE_YEAR);
    }

    @Test
    void 드래프트_계약금과_시작_레벨() {
        DraftRules draft = mlb.draft();
        assertThat(draft.teamsPerRound()).isEqualTo(30);
        assertThat(draft.signingBonus(1)).isEqualTo(9_500_000L);
        assertThat(draft.signingBonus(600)).isEqualTo(150_000L);
        assertThat(draft.startLevel(Education.COLLEGE, 10)).isEqualTo(LeagueLevel.A);
        assertThat(draft.startLevel(Education.COLLEGE, 11)).isEqualTo(LeagueLevel.ROOKIE);
        assertThat(draft.startLevel(Education.HIGH_SCHOOL, 1)).isEqualTo(LeagueLevel.ROOKIE);
    }

    @Test
    void 승강_판정은_기존_판정과_같다() {
        BattingLine weak = new BattingLine();
        weak.plateAppearances = 100;
        weak.atBats = 100;
        weak.hits = 30;
        for (LeagueLevel level : mlb.levels()) {
            for (double ovr : List.of(30.0, 50.0, 58.0, 70.0)) {
                Decision legacy = PromotionRules.evaluate(level, ovr, Position.SS, weak, null);
                Decision viaRuleset = PromotionRules.evaluate(mlb.promotion(), level, mlb.above(level),
                        mlb.below(level), ovr, Position.SS, weak, null);
                assertThat(viaRuleset).isEqualTo(legacy);
            }
        }
        assertThat(PromotionRules.isDemotionExempt(mlb.promotion(), LeagueLevel.MLB, 150)).isTrue();
        assertThat(PromotionRules.isDemotionExempt(mlb.promotion(), LeagueLevel.AAA, 150)).isFalse();
    }

    @Test
    void 포스트시즌은_4라운드_월드시리즈가_마지막() {
        PostseasonFormat format = mlb.postseason();
        assertThat(format.rounds()).hasSize(4);
        assertThat(format.finalRound()).isEqualTo(PostseasonRound.WORLD_SERIES.getNumber());
        assertThat(format.round(2)).isEqualTo(PostseasonRound.DIVISION);
        assertThat(format.seriesMvp(1)).isNull();
        assertThat(format.seriesMvp(3).type()).isEqualTo(AwardType.LCS_MVP);
        assertThat(format.seriesMvp(4).type()).isEqualTo(AwardType.WORLD_SERIES_MVP);
        assertThat(mlb.leagues()).containsExactly("AL", "NL");
    }
}
