package com.mlbgame.season.transition;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Position;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgingRulesTest {

    @Test
    void 서른하나까지는_노화가_없다() {
        assertThat(AgingRules.decline(Position.SS, 31)).isEmpty();
        assertThat(AgingRules.decline(Position.SP, 25)).isEmpty();
    }

    @Test
    void 나이_구간별로_1차_2차_하락군이_떨어진다() {
        Map<Attribute, Integer> at32 = AgingRules.decline(Position.SS, 32);
        assertThat(at32).containsEntry(Attribute.SPEED, -1).containsEntry(Attribute.FIELDING, -1)
                .doesNotContainKey(Attribute.POWER);

        Map<Attribute, Integer> at34 = AgingRules.decline(Position.OF, 34);
        assertThat(at34).containsEntry(Attribute.SPEED, -2).containsEntry(Attribute.POWER, -1);

        Map<Attribute, Integer> at37 = AgingRules.decline(Position.SP, 37);
        assertThat(at37).containsEntry(Attribute.VELOCITY, -3).containsEntry(Attribute.MOVEMENT, -2);
    }

    @Test
    void 경험_능력치는_떨어지지_않는다() {
        Map<Attribute, Integer> hitter = AgingRules.decline(Position.SS, 40);
        assertThat(hitter).doesNotContainKeys(Attribute.CONTACT, Attribute.AVOID_K, Attribute.EYE, Attribute.CLUTCH);
        Map<Attribute, Integer> pitcher = AgingRules.decline(Position.CP, 40);
        assertThat(pitcher).doesNotContainKeys(Attribute.CONTROL, Attribute.GROUNDBALL, Attribute.EFFICIENCY,
                Attribute.COMPOSURE, Attribute.PLATOON);
    }

    @Test
    void 강제_은퇴는_45세다() {
        assertThat(AgingRules.FORCED_RETIREMENT_AGE).isEqualTo(45);
    }
}
