package com.mlbgame.league.world;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldTeamRepository extends JpaRepository<WorldTeam, Long> {

    List<WorldTeam> findByWorldIdOrderByIdAsc(Long worldId);

    Optional<WorldTeam> findByWorldIdAndTeamId(Long worldId, Long teamId);
}
