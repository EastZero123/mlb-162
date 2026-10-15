package com.mlbgame.league.world;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueWorldRepository extends JpaRepository<LeagueWorld, Long> {

    Optional<LeagueWorld> findByPlayerId(Long playerId);
}
