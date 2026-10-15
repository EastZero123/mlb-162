package com.mlbgame.league.standings;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.TeamStrengthService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메이저리그 순위표. 저장하지 않고 월드 시드로 매번 같은 결과를 만든다.
 * 내 선수가 메이저에서 뛴 시리즈는 내 팀의 실제 결과로 바꾼다.
 */
@Service
public class StandingsService {

    private final TeamRepository teamRepository;
    private final TeamStrengthService teamStrengthService;

    public StandingsService(TeamRepository teamRepository, TeamStrengthService teamStrengthService) {
        this.teamRepository = teamRepository;
        this.teamStrengthService = teamStrengthService;
    }

    /**
     * @param seriesPlayed 지금까지 진행한 시리즈 수 (리그 전체가 같은 속도로 진행)
     * @param myTeamId     내 선수 소속 구단
     * @param myMlbResults 내 선수가 메이저에서 뛴 시리즈 번호 → 팀 [승, 패]
     */
    @Transactional(readOnly = true)
    public StandingsTable standings(LeagueWorld world, int year, int seriesPlayed, Long myTeamId,
                                    Map<Integer, int[]> myMlbResults) {
        List<Team> teams = teamRepository.findAllByOrderByIdAsc();
        Map<Long, int[]> records = StandingsGenerator.simulate(StandingsGenerator.seed(world.getSeed(), year),
                teams, overallRatings(world), seriesPlayed, myTeamId, myMlbResults);
        return StandingsGenerator.table(teams, records);
    }

    /** 구단 id → 메이저 종합 전력 (타선 50% / 선발 30% / 불펜 20%) */
    @Transactional(readOnly = true)
    public Map<Long, Double> overallRatings(LeagueWorld world) {
        Map<Long, Double> ratings = new HashMap<>();
        teamStrengthService.ratingsByTeam(world.getId(), LeagueLevel.MLB)
                .forEach((teamId, r) -> ratings.put(teamId, r.overall()));
        return ratings;
    }
}
