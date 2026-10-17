package com.mlbgame.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlayerTest {

    private static Player hitter(Position position, Education education, int startValue) {
        return Player.create(1L, "김유격", 7, position, Hand.R, Hand.R, Nationality.KOREA, education,
                SchoolRegion.OVERSEAS, startValue, Map.of(), List.of());
    }

    @Test
    void 배분한_포인트만큼_기본값에_더해진다() {
        Player player = Player.create(1L, "김거포", 25, Position.B1, Hand.L, Hand.L, Nationality.USA,
                Education.HIGH_SCHOOL, SchoolRegion.US_SOUTH, 25,
                Map.of(Attribute.POWER, 25, Attribute.GAP_POWER, 20, Attribute.CONTACT, 10), List.of());

        assertThat(player.attribute(Attribute.POWER)).isEqualTo(50);
        assertThat(player.attribute(Attribute.GAP_POWER)).isEqualTo(45);
        assertThat(player.attribute(Attribute.CONTACT)).isEqualTo(35);
        assertThat(player.attribute(Attribute.SPEED)).isEqualTo(25);
        // 기본 25 × 11 + 55 = 330 → OVR 30.0 (배분 방식과 무관하게 동일)
        assertThat(player.getOverall()).isEqualTo(30.0);
    }

    @Test
    void 야수는_능력치_11개가_모두_같은_시작값으로_생성된다() {
        Player player = hitter(Position.SS, Education.HIGH_SCHOOL, 30);

        assertThat(player.getAttributeViews()).hasSize(Attribute.COUNT_PER_POSITION)
                .allSatisfy(a -> assertThat(a.value()).isEqualTo(30));
        assertThat(player.getOverall()).isEqualTo(30.0);
        assertThat(player.getAge()).isEqualTo(18);
        assertThat(player.getNationality()).isEqualTo(Nationality.KOREA);
        assertThat(player.getSchoolRegion()).isEqualTo(SchoolRegion.OVERSEAS);
        assertThat(player.getStage()).isEqualTo(PlayerStage.CREATED);
    }

    @Test
    void 포수도_야수와_같은_능력치_11개를_쓴다() {
        Player catcher = hitter(Position.C, Education.COLLEGE, 40);
        Player shortstop = hitter(Position.SS, Education.COLLEGE, 40);

        assertThat(catcher.getAttributeViews()).extracting(Player.AttributeView::attribute)
                .containsExactlyElementsOf(shortstop.getAttributeViews().stream()
                        .map(Player.AttributeView::attribute).toList());
        assertThat(catcher.getAge()).isEqualTo(22);
    }

    @Test
    void 투수는_구종_능력치_없이_11개를_쓰고_구종은_레퍼토리로만_남는다() {
        Player closer = Player.create(1L, "최마무리", 99, Position.CP, Hand.L, Hand.L, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_WEST, 40, Map.of(),
                List.of(PitchType.SLIDER, PitchType.CURVE, PitchType.CHANGEUP));

        assertThat(closer.getAttributeViews()).hasSize(11)
                .extracting(Player.AttributeView::attribute)
                .contains(Attribute.PUT_AWAY, Attribute.RECOVERY)
                .doesNotContain(Attribute.CONTACT);
        assertThat(closer.getPitchRepertoire()).containsExactly(
                PitchType.FOUR_SEAM, PitchType.SLIDER, PitchType.CURVE, PitchType.CHANGEUP);
        assertThat(closer.getOverall()).isEqualTo(40.0);
    }

    @Test
    void 모든_능력치는_타자나_투수_중_하나_이상에서_쓰인다() {
        for (Attribute attribute : Attribute.values()) {
            boolean used = Attribute.forPosition(Position.SS).contains(attribute)
                    || Attribute.forPosition(Position.SP).contains(attribute);
            assertThat(used).as(attribute.name()).isTrue();
        }
        assertThat(Attribute.forPosition(Position.SS)).hasSize(Attribute.COUNT_PER_POSITION).doesNotHaveDuplicates();
        assertThat(Attribute.forPosition(Position.SP)).hasSize(Attribute.COUNT_PER_POSITION).doesNotHaveDuplicates();
    }

    @Test
    void 단계를_건너뛸_수_없다() {
        Player player = hitter(Position.SS, Education.HIGH_SCHOOL, 30);

        assertThatThrownBy(() -> player.draft(null, null)).isInstanceOf(InvalidStageException.class);
        player.completeCombine();
        assertThatThrownBy(player::completeCombine).isInstanceOf(InvalidStageException.class);
    }

    @Test
    void 마이너리그_소속은_구단명_뒤에_레벨을_붙여_표시한다() {
        Team tigers = Mockito.mock(Team.class);
        Mockito.when(tigers.getNameKo()).thenReturn("디트로이트 타이거스");

        Player player = hitter(Position.SS, Education.COLLEGE, 40);
        assertThat(player.getTeamDisplayName()).isNull();

        player.completeCombine();
        player.draft(tigers, LeagueLevel.A);
        assertThat(player.getTeamDisplayName()).isEqualTo("디트로이트 타이거스 A");
    }

    @Test
    @Tag("bugfix")
    void 노화로_훈련_비용이_진행도_아래로_줄면_바로_올리고_남은_진행도는_이월한다() {
        Player player = hitter(Position.SS, Education.COLLEGE, 90);   // 22세 · 90 → 비용 4
        for (int i = 0; i < 3; i++) {
            player.train(Attribute.POWER);
        }
        assertThat(player.trainingProgress(Attribute.POWER)).isEqualTo(3);

        List<String> news = player.applyAging(Map.of(Attribute.POWER, -1));   // 89 → 비용 2

        assertThat(player.attribute(Attribute.POWER)).isEqualTo(90);
        assertThat(player.trainingProgress(Attribute.POWER)).isEqualTo(1);
        assertThat(player.trainingCost(Attribute.POWER)).isEqualTo(4);
        assertThat(news).singleElement().asString().contains("파워", "+1", "1/4");
    }

    @Test
    @Tag("bugfix")
    void 노화_후에도_비용이_진행도보다_크면_진행도를_그대로_둔다() {
        Player player = hitter(Position.SS, Education.COLLEGE, 90);
        player.train(Attribute.POWER);

        List<String> news = player.applyAging(Map.of(Attribute.POWER, -1));   // 89 → 비용 2, 진행도 1

        assertThat(player.attribute(Attribute.POWER)).isEqualTo(89);
        assertThat(player.trainingProgress(Attribute.POWER)).isEqualTo(1);
        assertThat(news).isEmpty();
    }
}
