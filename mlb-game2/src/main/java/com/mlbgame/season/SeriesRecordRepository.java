package com.mlbgame.season;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeriesRecordRepository extends JpaRepository<SeriesRecord, Long> {

    List<SeriesRecord> findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(Long playerId, int seasonYear);

    Optional<SeriesRecord> findByPlayerIdAndSeasonYearAndSeriesNo(Long playerId, int seasonYear, int seriesNo);

    List<SeriesRecord> findByPlayerIdOrderBySeasonYearAscSeriesNoAsc(Long playerId);
}
