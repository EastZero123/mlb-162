package com.mlbgame.simulation;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.player.PitchType;
import com.mlbgame.player.Player;
import com.mlbgame.player.Position;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 시리즈 시작 시점의 내 선수 상태. 엔진은 {@link Player} 엔티티 대신 이 값만 쓴다.
 * JSON 으로 저장해 두면 시드와 함께 언제든 같은 시리즈를 다시 계산할 수 있다.
 */
public record PlayerSnapshot(
        Position position,
        Hand bats,
        Hand throwsHand,
        Map<Attribute, Integer> attributes,
        List<PitchType> repertoire
) {

    public static PlayerSnapshot of(Player player) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.forPosition(player.getPosition())) {
            attributes.put(attribute, player.attribute(attribute));
        }
        return new PlayerSnapshot(player.getPosition(), player.getBats(), player.getThrowsHand(),
                attributes, player.getPitchRepertoire());
    }

    public int attribute(Attribute attribute) {
        Integer value = attributes.get(attribute);
        if (value == null) {
            throw new IllegalArgumentException(position + " 포지션은 " + attribute + " 능력치가 없습니다.");
        }
        return value;
    }

    /** OVR = 포지션 능력치 11개의 단순 평균 */
    public double overall() {
        return Attribute.forPosition(position).stream().mapToInt(this::attribute).average().orElse(0);
    }
}
