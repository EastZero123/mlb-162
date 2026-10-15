package com.mlbgame.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mlbgame.league.LeagueLevel;
import org.junit.jupiter.api.Test;

class TradeRulesTest {

    @Test
    void 트레이드는_6번째부터_36번째_시리즈까지_메이저와_트리플A만() {
        assertThat(TradeRules.inWindow(5, LeagueLevel.MLB)).isFalse();
        assertThat(TradeRules.inWindow(6, LeagueLevel.MLB)).isTrue();
        assertThat(TradeRules.inWindow(36, LeagueLevel.AAA)).isTrue();
        assertThat(TradeRules.inWindow(37, LeagueLevel.MLB)).isFalse();
        assertThat(TradeRules.inWindow(20, LeagueLevel.AA)).isFalse();
    }

    @Test
    void 리빌딩_팀_고능력치_고연봉_요청거절은_확률을_높인다() {
        double base = TradeRules.chance(0.5, 60, 1_000_000, false, false);
        assertThat(base).isCloseTo(0.012, within(1e-9));
        assertThat(TradeRules.chance(0.40, 60, 1_000_000, false, false)).isCloseTo(0.024, within(1e-9));
        assertThat(TradeRules.chance(0.5, 75, 1_000_000, false, false)).isCloseTo(0.018, within(1e-9));
        assertThat(TradeRules.chance(0.5, 60, 20_000_000, true, false)).isCloseTo(0.024, within(1e-9));
        assertThat(TradeRules.chance(0.5, 60, 20_000_000, false, false)).isCloseTo(0.012, within(1e-9));
        assertThat(TradeRules.chance(0.5, 60, 1_000_000, false, true)).isCloseTo(0.048, within(1e-9));
    }

    @Test
    void 모든_조건이_겹쳐도_확률은_30퍼센트를_넘지_않는다() {
        // 1.2% × 2 × 1.5 × 2 × 4 = 28.8% — 현재 배수로는 상한(30%)에 닿지 않고, 상한은 배수 조정 시의 안전장치
        double all = TradeRules.chance(0.3, 90, 30_000_000, true, true);
        assertThat(all).isCloseTo(0.288, within(1e-9));
        assertThat(all).isLessThanOrEqualTo(TradeRules.MAX_CHANCE);
    }
}
