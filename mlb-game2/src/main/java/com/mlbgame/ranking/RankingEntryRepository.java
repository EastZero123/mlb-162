package com.mlbgame.ranking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingEntryRepository extends JpaRepository<RankingEntry, Long> {

    Optional<RankingEntry> findByPlayerId(Long playerId);

    boolean existsByPlayerId(Long playerId);

    List<RankingEntry> findByRetiredAtGreaterThanEqual(LocalDateTime from, Pageable pageable);

    List<RankingEntry> findByRetiredAtGreaterThanEqualAndMlbPaGreaterThanEqual(LocalDateTime from, int minPa,
                                                                              Pageable pageable);

    List<RankingEntry> findByRetiredAtGreaterThanEqualAndMlbOutsGreaterThanEqual(LocalDateTime from, int minOuts,
                                                                                Pageable pageable);
}
