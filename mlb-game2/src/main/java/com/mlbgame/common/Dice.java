package com.mlbgame.common;

/**
 * 게임 내 모든 랜덤의 진입점. 테스트에서는 고정값을 돌려주는 구현으로 바꿔 끼운다.
 * 랜덤은 서버에서만 굴린다 (요구사항 4장).
 */
public interface Dice {

    /** 0 이상 bound 미만 정수 */
    int nextInt(int bound);

    /** 0.0 이상 1.0 미만 */
    double nextDouble();

    /** 평균 0, 표준편차 1 정규분포 */
    double nextGaussian();
}
