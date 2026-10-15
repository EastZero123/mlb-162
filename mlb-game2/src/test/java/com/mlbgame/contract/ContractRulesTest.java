package com.mlbgame.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mlbgame.league.world.Market;
import com.mlbgame.league.world.Payroll;
import org.junit.jupiter.api.Test;

class ContractRulesTest {

    @Test
    void 메이저_46시리즈가_서비스_1년이다() {
        assertThat(ContractRules.serviceYears(45)).isZero();
        assertThat(ContractRules.serviceYears(46)).isEqualTo(1);
        assertThat(ContractRules.serviceYears(54 * 3)).isEqualTo(3);
    }

    @Test
    void 평가_OVR에는_지난_시즌_수상이_반영된다() {
        assertThat(ContractRules.valueOverall(70, 0)).isEqualTo(70);
        assertThat(ContractRules.valueOverall(70, 5)).isEqualTo(72.5);
        assertThat(ContractRules.valueOverall(70, 20)).isEqualTo(74);   // 최대 +4
    }

    @Test
    void 연봉조정은_서비스_연차별_시장가_비율이고_구단은_90퍼센트를_제시한다() {
        long fair3 = ContractRules.fairArbitration(3, 65);
        long fair5 = ContractRules.fairArbitration(5, 65);
        assertThat(fair3).isEqualTo(3_200_000L);         // 8M × 40%
        assertThat(fair5).isEqualTo(6_400_000L);         // 8M × 80%
        assertThat(ContractRules.arbitrationOffer(fair3)).isEqualTo(2_880_000L);
    }

    @Test
    void 청문회는_적정_연봉에_더_가까운_쪽이_이긴다() {
        long fair = 4_000_000;
        long offer = 3_600_000;
        assertThat(ContractRules.hearing(4_300_000, offer, fair)).isEqualTo(4_300_000);   // 0.3M vs 0.4M → 선수
        assertThat(ContractRules.hearing(5_000_000, offer, fair)).isEqualTo(offer);       // 1.0M vs 0.4M → 구단
        assertThat(ContractRules.hearing(4_400_000, offer, fair)).isEqualTo(offer);       // 같으면 구단
    }

    @Test
    void FA_역제안은_높게_부를수록_수락_확률이_떨어진다() {
        assertThat(ContractRules.counterAcceptance(9_000_000, 10_000_000)).isEqualTo(1);
        assertThat(ContractRules.counterAcceptance(11_000_000, 10_000_000)).isCloseTo(0.7, within(1e-9));
        assertThat(ContractRules.counterAcceptance(14_000_000, 10_000_000)).isZero();
    }

    @Test
    void 연장_계약은_FA까지_남은_기간을_덮고_시장가보다_싸다() {
        assertThat(ContractRules.extensionYears(2)).isEqualTo(6);
        assertThat(ContractRules.extensionYears(5)).isEqualTo(3);
        assertThat(ContractRules.extensionSalary(75)).isLessThan(Payroll.marketValue(75));
    }

    @Test
    void FA_오퍼는_빅마켓이_더_비싸고_젊을수록_길다() {
        assertThat(ContractRules.freeAgentSalary(75, Market.BIG, 0.5))
                .isGreaterThan(ContractRules.freeAgentSalary(75, Market.SMALL, 0.5));
        assertThat(ContractRules.freeAgentYears(27, 0.99)).isEqualTo(5);
        assertThat(ContractRules.freeAgentYears(35, 0.99)).isEqualTo(1);
    }
}
