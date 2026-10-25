package com.mlbgame.season.schedule;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.TeamStrengthService;
import com.mlbgame.simulation.game.TeamRatings;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 일정 + 시리즈별 양 팀 전력. 일정은 저장하지 않고 시드로 매번 같은 결과를 만든다.
 */
@Service
public class SeasonScheduleService {

    private final TeamRepository teamRepository;
    private final TeamStrengthService teamStrengthService;

    public SeasonScheduleService(TeamRepository teamRepository, TeamStrengthService teamStrengthService) {
        this.teamRepository = teamRepository;
        this.teamStrengthService = teamStrengthService;
    }

    public List<ScheduledSeries> schedule(LeagueWorld world, int year, Team myTeam) {
        long seed = ScheduleGenerator.seed(world.getSeed(), year, myTeam.getId());
        return LeagueRulesets.of(world).schedule(seed, myTeam, teamRepository.findByLeagueModeOrderByIdAsc(world.getLeagueMode()));
    }

    /** 일정과 그 시점의 양 팀 전력을 함께 묶은 매치업 목록 */
    @Transactional(readOnly = true)
    public List<Matchup> matchups(LeagueWorld world, int year, Team myTeam, LeagueLevel level) {
        Map<Long, TeamRatings> ratings = teamStrengthService.ratingsByTeam(world.getId(), level);
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
        return schedule(world, year, myTeam).stream()
                .map(s -> new Matchup(s, teams.get(s.opponentId()), ratings.get(myTeam.getId()), ratings.get(s.opponentId())))
                .toList();
    }

    public record Matchup(ScheduledSeries series, Team opponent, TeamRatings team, TeamRatings opponentRatings) {
    }
}
