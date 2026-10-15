package com.mlbgame.simulation;

/**
 * 타석 상황.
 *
 * @param runnersInScoringPosition 득점권(2·3루) 주자 여부 — 클러치·위기 관리 보정
 * @param batterFormOffset         타자 컨디션·피로 보정 (능력치 단위, 예: 피로 70 초과 시 −5)
 * @param pitcherFormOffset        투수 컨디션·피로 보정 (능력치 단위)
 */
public record PaContext(
        boolean runnersInScoringPosition,
        double batterFormOffset,
        double pitcherFormOffset
) {

    public static final PaContext NEUTRAL = new PaContext(false, 0, 0);

    public static PaContext scoringPosition(boolean risp) {
        return new PaContext(risp, 0, 0);
    }
}
