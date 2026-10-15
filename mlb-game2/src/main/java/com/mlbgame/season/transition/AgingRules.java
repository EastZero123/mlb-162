package com.mlbgame.season.transition;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Position;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 내 선수 노화 (시즌 전환 시, 랜덤 없음). 설계: docs/player-attributes.md 6장
 *
 * <pre>
 * 새 나이     1차 하락군   2차 하락군   나머지
 * ~30세       0            0            0
 * 31~32세     −1           −1           0
 * 33~34세     −2           −1           −1
 * 35~36세     −4           −3           −2
 * 37~38세     −6           −5           −4
 * 39세~       −7           −6           −5
 * </pre>
 * 훈련 비용({@code TrainingCost})과 함께 맞춘 곡선 (M5-3, 균형 훈련·스타 기준 투영):
 * 대졸 22세 40 → 30세 84 → 32~34세 정점 87 → 38세 76 → 40세 66 / 고졸 18세 30 → 30세 91 → 40세 71
 * 1차: 타자 주력·도루·수비 / 투수 구속·체력·회복력
 * 2차: 타자 파워·갭 파워·송구·내구성 / 투수 결정구·무브먼트·내구성
 * 나머지: 타자 컨택·삼진 회피·선구안·클러치 / 투수 제구·땅볼 유도·투구 효율·위기 관리·좌우 대응 (35세부터)
 */
public final class AgingRules {

    public static final int FORCED_RETIREMENT_AGE = 45;

    static final List<Attribute> HITTER_FIRST = List.of(Attribute.SPEED, Attribute.STEALING, Attribute.FIELDING);
    static final List<Attribute> HITTER_SECOND = List.of(Attribute.POWER, Attribute.GAP_POWER, Attribute.ARM, Attribute.DURABILITY);
    static final List<Attribute> PITCHER_FIRST = List.of(Attribute.VELOCITY, Attribute.STAMINA, Attribute.RECOVERY);
    static final List<Attribute> PITCHER_SECOND = List.of(Attribute.PUT_AWAY, Attribute.MOVEMENT, Attribute.DURABILITY);

    private AgingRules() {
    }

    /** 능력치별 하락량 (음수). 하락이 없으면 빈 맵 */
    public static Map<Attribute, Integer> decline(Position position, int newAge) {
        int first;
        int second;
        int rest;
        if (newAge >= 39) {
            first = -7;
            second = -6;
            rest = -5;
        } else if (newAge >= 37) {
            first = -6;
            second = -5;
            rest = -4;
        } else if (newAge >= 35) {
            first = -4;
            second = -3;
            rest = -2;
        } else if (newAge >= 33) {
            first = -2;
            second = -1;
            rest = -1;
        } else if (newAge >= 31) {
            first = -1;
            second = -1;
            rest = 0;
        } else {
            return Map.of();
        }
        List<Attribute> firstGroup = position.isPitcher() ? PITCHER_FIRST : HITTER_FIRST;
        List<Attribute> secondGroup = position.isPitcher() ? PITCHER_SECOND : HITTER_SECOND;
        Map<Attribute, Integer> result = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.forPosition(position)) {
            int delta = firstGroup.contains(attribute) ? first : secondGroup.contains(attribute) ? second : rest;
            if (delta != 0) {
                result.put(attribute, delta);
            }
        }
        return result;
    }
}
