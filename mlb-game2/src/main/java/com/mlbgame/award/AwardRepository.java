package com.mlbgame.award;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AwardRepository extends JpaRepository<Award, Long> {

    List<Award> findByPlayerIdOrderByIdAsc(Long playerId);

    List<Award> findByPlayerIdAndSeasonYearOrderByIdAsc(Long playerId, int seasonYear);

    List<Award> findByPlayerIdAndSeasonYearAndAfterSeries(Long playerId, int seasonYear, int afterSeries);

    boolean existsByPlayerIdAndSeasonYearAndType(Long playerId, int seasonYear, AwardType type);
}
