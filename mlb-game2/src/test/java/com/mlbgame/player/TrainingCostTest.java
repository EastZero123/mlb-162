package com.mlbgame.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrainingCostTest {

    @Test
    void 구간_비용과_나이_비용을_곱한다() {
        assertThat(TrainingCost.of(84, 29)).isEqualTo(1);
        assertThat(TrainingCost.of(85, 27)).isEqualTo(2);
        assertThat(TrainingCost.of(90, 22)).isEqualTo(4);
        assertThat(TrainingCost.of(95, 22)).isEqualTo(8);
        assertThat(TrainingCost.of(60, 30)).isEqualTo(2);
        assertThat(TrainingCost.of(88, 31)).isEqualTo(4);
        assertThat(TrainingCost.of(60, 33)).isEqualTo(4);
        assertThat(TrainingCost.of(60, 36)).isEqualTo(8);
        assertThat(TrainingCost.of(99, 40)).isEqualTo(64);
    }

    private Player player(int start) {
        return Player.create(1L, "비용", 7, Position.SS, Hand.R, Hand.R, Nationality.USA, Education.COLLEGE,
                SchoolRegion.US_EAST, start, Map.of(), List.of());
    }

    @Test
    void 비용만큼_훈련해야_능력치가_오르고_진행도는_초기화된다() {
        Player player = player(85);                       // 대졸 22세 · 85 → 2회마다 +1
        assertThat(player.trainingCost(Attribute.CONTACT)).isEqualTo(2);

        assertThat(player.train(Attribute.CONTACT)).isEqualTo(85);
        assertThat(player.trainingProgress(Attribute.CONTACT)).isEqualTo(1);
        assertThat(player.train(Attribute.CONTACT)).isEqualTo(86);
        assertThat(player.trainingProgress(Attribute.CONTACT)).isZero();
    }

    @Test
    void 나이가_들면_비용이_커진다() {
        Player player = player(60);
        assertThat(player.trainingCost(Attribute.POWER)).isEqualTo(1);
        for (int i = 0; i < 8; i++) {
            player.ageOneYear();                          // 22 → 30
        }
        assertThat(player.trainingCost(Attribute.POWER)).isEqualTo(2);
        player.train(Attribute.POWER);
        assertThat(player.attribute(Attribute.POWER)).isEqualTo(60);
        player.train(Attribute.POWER);
        assertThat(player.attribute(Attribute.POWER)).isEqualTo(61);
    }

    @Test
    void 스탯업_포인트는_비용_없이_바로_오른다() {
        Player player = player(90);
        player.addStatPoints(1);
        assertThat(player.spendStatPoint(Attribute.EYE)).isEqualTo(91);
    }
}
