package com.mlbgame.simulation;

import com.mlbgame.simulation.SimulationBalance.Baseline;

/**
 * 타석 상황.
 *
 * @param runnersInScoringPosition 득점권(2·3루) 주자 여부 — 클러치·위기 관리 보정
 * @param batterFormOffset         타자 컨디션·피로 보정 (능력치 단위, 예: 피로 70 초과 시 −5)
 * @param pitcherFormOffset        투수 컨디션·피로 보정 (능력치 단위)
 * @param baseline                 리그 평균 기록 (모드별). null 이면 MLB 기준
 */
public record PaContext(
        boolean runnersInScoringPosition,
        double batterFormOffset,
        double pitcherFormOffset,
        Baseline baseline
) {

    public static final PaContext NEUTRAL = new PaContext(false, 0, 0);

    public PaContext(boolean runnersInScoringPosition, double batterFormOffset, double pitcherFormOffset) {
        this(runnersInScoringPosition, batterFormOffset, pitcherFormOffset, null);
    }

    public static PaContext scoringPosition(boolean risp) {
        return new PaContext(risp, 0, 0);
    }

    /** 같은 상황, 다른 리그 기준 */
    public PaContext withBaseline(Baseline leagueBaseline) {
        return new PaContext(runnersInScoringPosition, batterFormOffset, pitcherFormOffset, leagueBaseline);
    }
}
