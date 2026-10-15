package com.mlbgame.league;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.league.standings.StandingsService;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.stats.Leaderboard;
import com.mlbgame.league.stats.Leaderboard.Candidate;
import com.mlbgame.league.stats.Leaderboard.Category;
import com.mlbgame.league.stats.LeagueStatsService;
import com.mlbgame.league.stats.LeagueStatsService.PlayerLine;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonService;
import com.mlbgame.season.SeasonService.MlbSeason;
import com.mlbgame.season.schedule.ScheduledSeries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 메이저리그 순위표 + 리더보드 (내 선수가 메이저에서 뛰었으면 리더보드에 포함) */
@Controller
public class LeagueController {

    private final PlayerService playerService;
    private final SeasonService seasonService;
    private final LeagueWorldService leagueWorldService;
    private final StandingsService standingsService;
    private final LeagueStatsService leagueStatsService;

    public LeagueController(PlayerService playerService, SeasonService seasonService,
                            LeagueWorldService leagueWorldService, StandingsService standingsService,
                            LeagueStatsService leagueStatsService) {
        this.playerService = playerService;
        this.seasonService = seasonService;
        this.leagueWorldService = leagueWorldService;
        this.standingsService = standingsService;
        this.leagueStatsService = leagueStatsService;
    }

    @GetMapping("/players/{playerId}/league")
    public String overview(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        PlayerSeason season = seasonService.currentSeason(authUser.getId(), playerId);
        LeagueWorld world = leagueWorldService.getOrCreate(playerId);
        MlbSeason mlb = seasonService.mlbSeason(authUser.getId(), playerId);

        StandingsTable table = standingsService.standings(world, season.getSeasonYear(), season.getSeriesPlayed(),
                player.getTeam().getId(), mlb.teamResults());

        int completedMonths = season.getSeriesPlayed() / ScheduledSeries.SERIES_PER_MONTH;
        int teamGames = completedMonths * LeagueStatsService.GAMES_PER_MONTH;
        List<Candidate> candidates = new ArrayList<>();
        for (PlayerLine line : leagueStatsService.lines(world, season.getSeasonYear(),
                LeagueStatsService.monthsThrough(completedMonths))) {
            candidates.add(new Candidate(line.npc().getName(), line.team().getNameKo(),
                    line.isPitcher() ? null : line.batting(), line.isPitcher() ? line.pitching() : null, false));
        }
        if (mlb.played()) {
            candidates.add(new Candidate(player.getName(), player.getTeam().getNameKo(),
                    player.isPitcher() ? null : mlb.batting(), player.isPitcher() ? mlb.pitching() : null, true));
        }
        Map<Category, List<Leaderboard.Entry>> leaders = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            leaders.put(category, Leaderboard.top(candidates, category, teamGames));
        }

        model.addAttribute("player", player);
        model.addAttribute("season", season);
        model.addAttribute("table", table);
        model.addAttribute("leagues", StandingsTable.LEAGUES);
        model.addAttribute("divisions", StandingsTable.DIVISIONS);
        model.addAttribute("myTeamId", player.getTeam().getId());
        model.addAttribute("leaders", leaders);
        model.addAttribute("completedMonths", completedMonths);
        return "league/overview";
    }
}
