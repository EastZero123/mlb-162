package com.mlbgame.simulation;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;

/**
 * 타석 계산에 쓰는 타자 능력치 묶음. 내 선수는 {@link #of(PlayerSnapshot)}, NPC 는 {@link #average(int)}.
 * 시드 재현을 위해 시리즈 시작 시점의 값으로 고정해서 쓴다.
 *
 * @param bats null 이면 좌우 상성을 계산하지 않음
 */
public record BatterProfile(
        int contact,
        int avoidK,
        int eye,
        int power,
        int gapPower,
        int speed,
        int clutch,
        Hand bats
) {

    public static BatterProfile of(PlayerSnapshot player) {
        return new BatterProfile(
                player.attribute(Attribute.CONTACT),
                player.attribute(Attribute.AVOID_K),
                player.attribute(Attribute.EYE),
                player.attribute(Attribute.POWER),
                player.attribute(Attribute.GAP_POWER),
                player.attribute(Attribute.SPEED),
                player.attribute(Attribute.CLUTCH),
                player.bats());
    }

    /** 모든 능력치가 rating 인 평균 타자 */
    public static BatterProfile average(int rating) {
        return new BatterProfile(rating, rating, rating, rating, rating, rating, rating, null);
    }

    public BatterProfile withBats(Hand hand) {
        return new BatterProfile(contact, avoidK, eye, power, gapPower, speed, clutch, hand);
    }
}
