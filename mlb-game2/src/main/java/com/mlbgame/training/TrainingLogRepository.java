package com.mlbgame.training;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingLogRepository extends JpaRepository<TrainingLog, Long> {

    List<TrainingLog> findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(Long playerId, int seasonYear);

    long countByPlayerIdAndSeasonYear(Long playerId, int seasonYear);
}
