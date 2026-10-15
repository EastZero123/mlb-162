package com.mlbgame.season;

import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.rules.Condition;
import com.mlbgame.simulation.rules.InjuryRules.Injury;
import com.mlbgame.simulation.series.SeriesResult;

/**
 * DB 에 저장하는 시리즈 요약 (series_records.summary_json). 하이라이트·경기별 상세는 넣지 않는다.
 */
public record SeriesSummary(
        int wins,
        int losses,
        BattingLine batting,
        FieldingLine fielding,
        PitchingLine pitching,
        Condition condition,
        int endFatigue,
        Injury injury
) {

    public static SeriesSummary of(SeriesResult result) {
        return new SeriesSummary(result.wins(), result.losses(), result.batting(), result.fielding(),
                result.pitching(), result.condition(), result.endFatigue(), result.injury());
    }
}
