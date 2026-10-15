package com.mlbgame.season;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSeasonRepository extends JpaRepository<PlayerSeason, Long> {

    Optional<PlayerSeason> findByPlayerIdAndSeasonYear(Long playerId, int seasonYear);

    List<PlayerSeason> findByPlayerIdOrderBySeasonYearAsc(Long playerId);
}
