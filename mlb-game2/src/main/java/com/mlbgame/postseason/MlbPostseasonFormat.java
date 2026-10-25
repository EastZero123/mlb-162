package com.mlbgame.postseason;

import com.mlbgame.award.AwardType;
import com.mlbgame.league.standings.StandingsTable;
import java.util.List;

/** MLB 포스트시즌 (리그당 6팀, 와일드카드 → 디비전 → 챔피언십 → 월드시리즈). 대진 규칙: {@link PostseasonEngine} */
public final class MlbPostseasonFormat implements PostseasonFormat {

    public static final MlbPostseasonFormat INSTANCE = new MlbPostseasonFormat();

    private MlbPostseasonFormat() {
    }

    @Override
    public List<PostseasonRound> rounds() {
        return List.of(PostseasonRound.values());
    }

    @Override
    public Bracket initial(StandingsTable table) {
        return PostseasonEngine.initial(table);
    }

    @Override
    public void createNextRound(Bracket bracket, int completedRound) {
        PostseasonEngine.createNextRound(bracket, completedRound);
    }

    @Override
    public SeriesMvp seriesMvp(int round) {
        if (round == PostseasonRound.CHAMPIONSHIP.getNumber()) {
            return new SeriesMvp(AwardType.LCS_MVP, PostseasonService.LCS_MARKER);
        }
        if (round == PostseasonRound.WORLD_SERIES.getNumber()) {
            return new SeriesMvp(AwardType.WORLD_SERIES_MVP, PostseasonService.WS_MARKER);
        }
        return null;
    }
}
