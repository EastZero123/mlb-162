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
 * 새 나이     1차 하락군   2차 하락군
 * ~31세       0            0
 * 32~33세     −1           0
 * 34~35세     −2           −1
 * 36세~       −3           −2
 * </pre>
 * 1차: 타자 주력·도루·수비 / 투수 구속·체력·회복력
 * 2차: 타자 파워·갭 파워·송구·내구성 / 투수 결정구·무브먼트·내구성
 * 나머지(컨택·삼진 회피·선구안·클러치 / 제구·땅볼 유도·투구 효율·위기 관리·좌우 대응)는 하락 없음
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
        if (newAge >= 36) {
            first = -3;
            second = -2;
        } else if (newAge >= 34) {
            first = -2;
            second = -1;
        } else if (newAge >= 32) {
            first = -1;
            second = 0;
        } else {
            return Map.of();
        }
        Map<Attribute, Integer> result = new EnumMap<>(Attribute.class);
        (position.isPitcher() ? PITCHER_FIRST : HITTER_FIRST).forEach(a -> result.put(a, first));
        if (second != 0) {
            (position.isPitcher() ? PITCHER_SECOND : HITTER_SECOND).forEach(a -> result.put(a, second));
        }
        return result;
    }
}
