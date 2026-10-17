package com.mlbgame.postseason;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostseasonRepository extends JpaRepository<Postseason, Long> {

    Optional<Postseason> findByPlayerIdAndSeasonYear(Long playerId, int seasonYear);

    List<Postseason> findByPlayerIdOrderBySeasonYearAsc(Long playerId);
}
