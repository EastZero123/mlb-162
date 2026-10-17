package com.mlbgame.league.stats;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueSeasonAverageRepository extends JpaRepository<LeagueSeasonAverage, Long> {

    Optional<LeagueSeasonAverage> findByWorldIdAndSeasonYear(Long worldId, int seasonYear);
}
