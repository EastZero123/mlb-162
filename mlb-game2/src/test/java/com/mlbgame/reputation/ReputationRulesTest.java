package com.mlbgame.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mlbgame.contract.ContractRules;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.trade.TradeRules;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 평판 규칙: 평판 50 이면 도입 전과 같고, 높을수록 유리·낮을수록 불리 */
@Tag("bugfix")
class ReputationRulesTest {

    @Test
    void 등급_경계() {
        assertThat(ReputationRules.grade(80)).isEqualTo("존경받음");
        assertThat(ReputationRules.grade(79)).isEqualTo("좋음");
        assertThat(ReputationRules.grade(60)).isEqualTo("좋음");
        assertThat(ReputationRules.grade(40)).isEqualTo("보통");
        assertThat(ReputationRules.grade(20)).isEqualTo("나쁨");
        assertThat(ReputationRules.grade(19)).isEqualTo("최악");
    }

    @Test
    void 평판_50이면_모든_보정이_도입_전과_같다() {
        assertThat(TradeRules.requestSuccess(50)).isCloseTo(TradeRules.REQUEST_SUCCESS, within(1e-9));
        assertThat(ReputationRules.autoTradeFactor(50)).isEqualTo(1.0);
        assertThat(ContractRules.counterAcceptance(110, 100, 50))
                .isCloseTo(ContractRules.counterAcceptance(110, 100), within(1e-9))
                .isCloseTo(0.7, within(1e-9));
        assertThat(ReputationRules.freeAgentOfferAdjustment(50)).isZero();
        assertThat(ReputationRules.freeAgentSalaryFactor(50)).isCloseTo(1.0, within(1e-9));
        assertThat(ReputationRules.extensionChance(0.35, 50)).isCloseTo(0.35, within(1e-9));
    }

    @Test
    void 트레이드_요청_성공률은_평판_0에서_18퍼센트_100에서_42퍼센트() {
        assertThat(TradeRules.requestSuccess(0)).isCloseTo(0.18, within(1e-9));
        assertThat(TradeRules.requestSuccess(100)).isCloseTo(0.42, within(1e-9));
    }

    @Test
    void 평판_30_미만이면_자동_트레이드_확률이_1점5배() {
        double normal = TradeRules.chance(0.5, 60, 1_000_000, false, false, 30);
        double bad = TradeRules.chance(0.5, 60, 1_000_000, false, false, 29);
        assertThat(bad / normal).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void 역제안_결렬_위험은_평판_100이면_절반_0이면_1점5배() {
        // 10% 높게 불렀을 때: 결렬 30% → 평판 100 15%, 평판 0 45%
        assertThat(ContractRules.counterAcceptance(110, 100, 100)).isCloseTo(0.85, within(1e-9));
        assertThat(ContractRules.counterAcceptance(110, 100, 0)).isCloseTo(0.55, within(1e-9));
        assertThat(ContractRules.counterAcceptance(100, 100, 0)).isEqualTo(1.0);       // 오퍼 이하는 늘 수락
    }

    @Test
    void FA_오퍼와_연장_제안_보정() {
        assertThat(ReputationRules.freeAgentOfferAdjustment(70)).isEqualTo(1);
        assertThat(ReputationRules.freeAgentOfferAdjustment(29)).isEqualTo(-1);
        assertThat(ReputationRules.freeAgentSalaryFactor(100)).isCloseTo(1.05, within(1e-9));
        assertThat(ReputationRules.freeAgentSalaryFactor(0)).isCloseTo(0.95, within(1e-9));
        assertThat(ReputationRules.extensionChance(0.35, 100)).isCloseTo(0.60, within(1e-9));   // 최대 60%
        assertThat(ReputationRules.extensionChance(0.35, 0)).isZero();
    }

    @Test
    void 시즌_수상_보너스는_최대_5() {
        assertThat(ReputationRules.awardBonus(0)).isZero();
        assertThat(ReputationRules.awardBonus(3)).isEqualTo(3);
        assertThat(ReputationRules.awardBonus(12)).isEqualTo(5);
    }

    @Test
    void 평판은_0에서_100_사이로_잘린다() {
        Player player = Player.create(1L, "평판", 1, Position.SS, Hand.R, Hand.R, Nationality.KOREA, Education.COLLEGE,
                SchoolRegion.US_EAST, 50, Map.of(), List.of());
        assertThat(player.getReputation()).isEqualTo(ReputationRules.START);

        assertThat(player.changeReputation(70)).isEqualTo(50);
        assertThat(player.getReputation()).isEqualTo(100);
        assertThat(player.changeReputation(-130)).isEqualTo(-100);
        assertThat(player.getReputation()).isZero();
    }
}
