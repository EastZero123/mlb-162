package com.mlbgame.postseason;

/** 포스트시즌 라운드 한 개의 진행 방식 (모드별 {@link PostseasonFormat} 이 목록을 준다) */
public interface RoundSpec {

    /** 1부터 */
    int getNumber();

    String getLabel();

    int getMaxGames();

    /** 시리즈를 이기는 데 필요한 승수 */
    default int winsNeeded() {
        return getMaxGames() / 2 + 1;
    }

    /** gameIndex(0부터) 경기가 상위 시드 홈인지 */
    boolean highSeedHome(int gameIndex);

    /** 상위 시드가 안고 시작하는 승수 (KBO 와일드카드 결정전 1승 어드밴티지 등). 기본 0 */
    default int highSeedStartWins() {
        return 0;
    }
}
