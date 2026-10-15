package com.mlbgame.simulation.series;

import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.GameResult;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.rules.Condition;
import com.mlbgame.simulation.rules.InjuryRules.Injury;
import com.mlbgame.simulation.rules.PitcherUsage;
import java.util.List;

/**
 * 시리즈 결과.
 *
 * @param games      경기별 결과
 * @param wins       내 팀 승수
 * @param losses     내 팀 패수
 * @param batting    내 선수 시리즈 타격 합계 (투수면 null)
 * @param fielding   내 선수 시리즈 수비 합계 (투수면 null)
 * @param pitching   내 선수 시리즈 투구 합계 (타자면 null)
 * @param condition  이번 시리즈 컨디션
 * @param endFatigue 시리즈 종료 시 피로도
 * @param endUsage   시리즈 종료 시 투수 등판 상태
 * @param injury     이번 시리즈에서 당한 부상 (없으면 null). 부상 이후 경기는 결장
 * @param highlights "2차전 7회말 역전 투런 홈런!" 형식
 */
public record SeriesResult(
        List<GameResult> games,
        int wins,
        int losses,
        BattingLine batting,
        FieldingLine fielding,
        PitchingLine pitching,
        Condition condition,
        int endFatigue,
        PitcherUsage endUsage,
        Injury injury,
        List<String> highlights
) {
}
