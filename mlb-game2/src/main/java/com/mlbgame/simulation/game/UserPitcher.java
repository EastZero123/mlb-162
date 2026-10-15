package com.mlbgame.simulation.game;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.PitchType;
import com.mlbgame.simulation.PitcherProfile;
import com.mlbgame.simulation.PlayerSnapshot;
import java.util.List;

/**
 * 경기에 나서는 내 투수.
 *
 * @param pitching   타석 계산용 능력치
 * @param stamina    체력 (SP: 투구 수 한도)
 * @param efficiency 투구 효율 (타자당 투구 수)
 * @param role       오늘의 역할
 * @param formOffset 컨디션·피로·연투 보정 (능력치 단위)
 * @param repertoire 구종 (하이라이트 문구용)
 */
public record UserPitcher(
        PitcherProfile pitching,
        int stamina,
        int efficiency,
        PitcherRole role,
        double formOffset,
        List<PitchType> repertoire
) {

    public static UserPitcher of(PlayerSnapshot player, PitcherRole role, double formOffset) {
        return new UserPitcher(PitcherProfile.of(player),
                player.attribute(Attribute.STAMINA),
                player.attribute(Attribute.EFFICIENCY),
                role, formOffset, player.repertoire());
    }

    public enum PitcherRole {
        /** 오늘 선발 등판 */
        STARTER,
        /** 마무리: 9회 이후 세이브 상황(1~3점 리드) 또는 동점이면 등판 */
        CLOSER,
        /** 마무리 점검 등판: 4일 이상 쉬어 점수와 상관없이 9회에 등판 */
        CLOSER_TUNE_UP,
        /** 오늘은 등판하지 않음 (선발 휴식일, 연투 제한) */
        NONE
    }
}
