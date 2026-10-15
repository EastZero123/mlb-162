package com.mlbgame.simulation.game;

import java.util.List;

/**
 * 경기 결과.
 *
 * @param teamScore      내 팀 득점
 * @param opponentScore  상대 팀 득점
 * @param innings        진행 이닝 수 (연장 포함)
 * @param teamLine       내 팀 이닝별 득점
 * @param opponentLine   상대 팀 이닝별 득점
 * @param participated   내 선수가 출전했는지
 * @param batting        내 선수 타격 기록 (투수면 null)
 * @param fielding       내 선수 수비 기록 (투수면 null)
 * @param pitching       내 선수 투구 기록 (타자거나 등판 안 했으면 null)
 * @param highlights     내 선수 하이라이트 문구
 */
public record GameResult(
        int teamScore,
        int opponentScore,
        int innings,
        List<Integer> teamLine,
        List<Integer> opponentLine,
        boolean participated,
        BattingLine batting,
        FieldingLine fielding,
        PitchingLine pitching,
        List<String> highlights
) {

    public boolean isWin() {
        return teamScore > opponentScore;
    }
}
