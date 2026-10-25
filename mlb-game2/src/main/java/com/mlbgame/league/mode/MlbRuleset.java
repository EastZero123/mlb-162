package com.mlbgame.league.mode;

import com.mlbgame.award.AwardCatalog;
import com.mlbgame.combine.DraftRules;
import com.mlbgame.contract.ContractRules;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.Team;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.world.PayrollRules;
import com.mlbgame.postseason.MlbPostseasonFormat;
import com.mlbgame.postseason.PostseasonFormat;
import com.mlbgame.season.promotion.PromotionRules;
import com.mlbgame.season.schedule.ScheduleGenerator;
import com.mlbgame.season.schedule.ScheduledSeries;
import java.util.List;

/** MLB 규칙: 지금까지의 게임 규칙을 그대로 모은 것 (값은 각 규칙 클래스의 MLB 상수) */
public final class MlbRuleset implements LeagueRuleset {

    static final int ALL_STAR_AFTER = 27;

    @Override
    public LeagueMode mode() {
        return LeagueMode.MLB;
    }

    @Override
    public int seriesPerSeason() {
        return ScheduleGenerator.SERIES_PER_SEASON;
    }

    @Override
    public int gamesInSeries(int seriesNo) {
        return ScheduledSeries.DEFAULT_GAMES;
    }

    @Override
    public List<ScheduledSeries> schedule(long seed, Team myTeam, List<Team> teams) {
        return ScheduleGenerator.generate(seed, myTeam, teams);
    }

    @Override
    public List<String> leagues() {
        return StandingsTable.LEAGUES;
    }

    @Override
    public List<String> divisions() {
        return StandingsTable.DIVISIONS;
    }

    @Override
    public PostseasonFormat postseason() {
        return MlbPostseasonFormat.INSTANCE;
    }

    @Override
    public PromotionRules.Params promotion() {
        return PromotionRules.MLB;
    }

    @Override
    public LeagueTerms terms() {
        return LeagueTerms.MLB;
    }

    @Override
    public AwardCatalog awards() {
        return AwardCatalog.MLB;
    }

    @Override
    public int allStarAfterSeries() {
        return ALL_STAR_AFTER;
    }

    @Override
    public PayrollRules payroll() {
        return PayrollRules.MLB;
    }

    @Override
    public int seriesPerServiceYear() {
        return ContractRules.SERIES_PER_SERVICE_YEAR;
    }

    @Override
    public DraftRules draft() {
        return DraftRules.MLB;
    }
}
