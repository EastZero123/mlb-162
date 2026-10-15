package com.mlbgame.simulation.game;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.BatterProfile;
import com.mlbgame.simulation.PlayerSnapshot;

/**
 * 경기에 나서는 내 타자.
 *
 * @param batting     타석 계산용 능력치
 * @param stealing    도루
 * @param fielding    수비 (포수: 블로킹·프레이밍)
 * @param arm         송구 (포수: 도루 저지)
 * @param position    수비 위치 — 타구가 오는 빈도가 다름
 * @param battingSlot 타순 1~9 (선발일 때)
 * @param role        선발 / 대타 / 결장
 * @param formOffset  컨디션·피로 보정 (능력치 단위)
 */
public record UserHitter(
        BatterProfile batting,
        int stealing,
        int fielding,
        int arm,
        Position position,
        int battingSlot,
        HitterRole role,
        double formOffset
) {

    public static UserHitter of(PlayerSnapshot player, int battingSlot, HitterRole role, double formOffset) {
        return new UserHitter(BatterProfile.of(player),
                player.attribute(Attribute.STEALING),
                player.attribute(Attribute.FIELDING),
                player.attribute(Attribute.ARM),
                player.position(), battingSlot, role, formOffset);
    }

    public boolean starts() {
        return role == HitterRole.STARTER;
    }

    public enum HitterRole {
        /** 선발 출전: 정해진 타순에서 끝까지 */
        STARTER,
        /** 벤치에서 대기하다 7회 이후 9번 타순에 대타로 한 타석 */
        PINCH_HITTER,
        /** 결장 */
        NONE
    }
}
