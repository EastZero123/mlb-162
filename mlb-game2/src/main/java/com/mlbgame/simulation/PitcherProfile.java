package com.mlbgame.simulation;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;

/**
 * 타석 계산에 쓰는 투수 능력치 묶음. 체력·투구 효율·회복력·내구성은 경기 진행(이닝·등판) 단계에서 쓴다.
 *
 * @param throwsHand null 이면 좌우 상성을 계산하지 않음
 */
public record PitcherProfile(
        int velocity,
        int control,
        int putAway,
        int movement,
        int groundball,
        int composure,
        int platoon,
        Hand throwsHand
) {

    public static PitcherProfile of(PlayerSnapshot player) {
        return new PitcherProfile(
                player.attribute(Attribute.VELOCITY),
                player.attribute(Attribute.CONTROL),
                player.attribute(Attribute.PUT_AWAY),
                player.attribute(Attribute.MOVEMENT),
                player.attribute(Attribute.GROUNDBALL),
                player.attribute(Attribute.COMPOSURE),
                player.attribute(Attribute.PLATOON),
                player.throwsHand());
    }

    /** 모든 능력치가 rating 인 평균 투수 */
    public static PitcherProfile average(int rating) {
        return new PitcherProfile(rating, rating, rating, rating, rating, rating, rating, null);
    }

    public PitcherProfile withThrows(Hand hand) {
        return new PitcherProfile(velocity, control, putAway, movement, groundball, composure, platoon, hand);
    }
}
