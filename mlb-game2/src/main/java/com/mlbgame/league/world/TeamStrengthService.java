package com.mlbgame.league.world;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.simulation.game.TeamRatings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 월드 안의 구단 전력 조회 (레벨별) */
@Service
public class TeamStrengthService {

    private final WorldTeamRepository worldTeamRepository;
    private final NpcPlayerRepository npcPlayerRepository;

    public TeamStrengthService(WorldTeamRepository worldTeamRepository, NpcPlayerRepository npcPlayerRepository) {
        this.worldTeamRepository = worldTeamRepository;
        this.npcPlayerRepository = npcPlayerRepository;
    }

    /** 구단 id → 해당 레벨 팀 전력 (메이저는 로스터, 마이너는 팜 점수) */
    @Transactional(readOnly = true)
    public Map<Long, TeamRatings> ratingsByTeam(Long worldId, LeagueLevel level) {
        Map<Long, TeamRatings> result = new HashMap<>();
        List<WorldTeam> teams = worldTeamRepository.findByWorldIdOrderByIdAsc(worldId);
        if (level.isTop()) {
            Map<Long, List<NpcPlayer>> rosters = npcPlayerRepository.findByWorldIdAndStatus(worldId, NpcStatus.MLB)
                    .stream().collect(Collectors.groupingBy(NpcPlayer::getTeamId));
            for (WorldTeam team : teams) {
                result.put(team.getTeam().getId(), TeamStrength.fromNpcs(rosters.getOrDefault(team.getTeam().getId(), List.of())));
            }
        } else {
            for (WorldTeam team : teams) {
                result.put(team.getTeam().getId(), TeamStrength.forMinors(level, team.getFarmScore()));
            }
        }
        return result;
    }
}
