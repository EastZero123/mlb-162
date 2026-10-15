package com.mlbgame.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 밸런스 수치. application.yml 의 game.balance.* 로 조정한다. 설계: docs/player-attributes.md
 *
 * @param maxActivePlayers          계정당 활성 선수 슬롯 수
 * @param highSchoolStart           고졸 선수의 기본 능력치 (11개 모두 동일, 배분 전)
 * @param collegeStart              대졸 선수의 기본 능력치 (11개 모두 동일, 배분 전)
 * @param allocationPoints          생성 시 자유 배분 포인트 (전부 사용해야 함)
 * @param allocationMaxPerAttribute 능력치 하나에 배분할 수 있는 최대 포인트
 * @param highSchoolScoutBonus      컴바인 종합 점수에 더하는 고졸 나이 프리미엄
 */
@ConfigurationProperties("game.balance")
public record GameBalance(
        int maxActivePlayers,
        int highSchoolStart,
        int collegeStart,
        int allocationPoints,
        int allocationMaxPerAttribute,
        int highSchoolScoutBonus
) {
}
