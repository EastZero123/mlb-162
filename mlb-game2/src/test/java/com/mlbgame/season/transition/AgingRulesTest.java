package com.mlbgame.season.transition;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Position;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgingRulesTest {

    @Test
    void 서른까지는_노화가_없다() {
        assertThat(AgingRules.decline(Position.SS, 30)).isEmpty();
        assertThat(AgingRules.decline(Position.SP, 25)).isEmpty();
    }

    @Test
    void 나이_구간별로_1차_2차_하락군이_떨어진다() {
        Map<Attribute, Integer> at31 = AgingRules.decline(Position.SS, 31);
        assertThat(at31).containsEntry(Attribute.SPEED, -1).containsEntry(Attribute.POWER, -1)
                .doesNotContainKey(Attribute.CONTACT);

        Map<Attribute, Integer> at34 = AgingRules.decline(Position.OF, 34);
        assertThat(at34).containsEntry(Attribute.SPEED, -2).containsEntry(Attribute.POWER, -1);

        Map<Attribute, Integer> at36 = AgingRules.decline(Position.SP, 36);
        assertThat(at36).containsEntry(Attribute.VELOCITY, -4).containsEntry(Attribute.MOVEMENT, -3);

        Map<Attribute, Integer> at38 = AgingRules.decline(Position.SP, 38);
        assertThat(at38).containsEntry(Attribute.VELOCITY, -6).containsEntry(Attribute.MOVEMENT, -5);

        Map<Attribute, Integer> at40 = AgingRules.decline(Position.SS, 40);
        assertThat(at40).containsEntry(Attribute.SPEED, -7).containsEntry(Attribute.POWER, -6);
    }

    @Test
    void 경험_능력치는_32세까지_유지되고_33세부터_떨어진다() {
        assertThat(AgingRules.decline(Position.SS, 32))
                .doesNotContainKeys(Attribute.CONTACT, Attribute.AVOID_K, Attribute.EYE, Attribute.CLUTCH);
        assertThat(AgingRules.decline(Position.CP, 32))
                .doesNotContainKeys(Attribute.CONTROL, Attribute.GROUNDBALL, Attribute.EFFICIENCY,
                        Attribute.COMPOSURE, Attribute.PLATOON);

        assertThat(AgingRules.decline(Position.SS, 33)).containsEntry(Attribute.CONTACT, -1).containsEntry(Attribute.EYE, -1);
        assertThat(AgingRules.decline(Position.CP, 36)).containsEntry(Attribute.CONTROL, -2);
        assertThat(AgingRules.decline(Position.SS, 40)).containsEntry(Attribute.CLUTCH, -5);
    }

    @Test
    void 서른셋부터는_포지션_능력치가_모두_떨어진다() {
        assertThat(AgingRules.decline(Position.SS, 33)).containsOnlyKeys(Attribute.forPosition(Position.SS));
        assertThat(AgingRules.decline(Position.SP, 38)).containsOnlyKeys(Attribute.forPosition(Position.SP));
    }

    @Test
    void 강제_은퇴는_45세다() {
        assertThat(AgingRules.FORCED_RETIREMENT_AGE).isEqualTo(45);
    }
}
