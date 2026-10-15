package com.mlbgame.season.promotion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelChangeRepository extends JpaRepository<LevelChange, Long> {

    List<LevelChange> findByPlayerIdOrderByIdAsc(Long playerId);

    Optional<LevelChange> findByPlayerIdAndSeasonYearAndAfterSeries(Long playerId, int seasonYear, int afterSeries);
}
