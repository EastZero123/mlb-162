package com.mlbgame.simulation.game;

/**
 * 경기 엔진에 들어가는 팀 전력 (평균 능력치).
 *
 * @param offense  타선 (주전 9명 평균)
 * @param rotation 선발진 (1~6회 투수)
 * @param bullpen  불펜 (7회 이후 투수)
 */
public record TeamRatings(int offense, int rotation, int bullpen) {

    /** 세 부문이 모두 같은 팀 */
    public static TeamRatings uniform(int rating) {
        return new TeamRatings(rating, rating, rating);
    }

    /** 화면 표시·기대 승률용 종합 전력 (타선 50% / 선발 30% / 불펜 20%) */
    public double overall() {
        return offense * 0.5 + rotation * 0.3 + bullpen * 0.2;
    }
}
