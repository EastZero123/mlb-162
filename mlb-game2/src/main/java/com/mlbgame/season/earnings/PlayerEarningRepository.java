package com.mlbgame.season.earnings;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayerEarningRepository extends JpaRepository<PlayerEarning, Long> {

    Optional<PlayerEarning> findByPlayerIdAndSeasonYearAndType(Long playerId, int seasonYear, EarningType type);

    List<PlayerEarning> findByPlayerIdOrderBySeasonYearAsc(Long playerId);

    @Query("select coalesce(sum(e.amount), 0) from PlayerEarning e where e.playerId = :playerId")
    long totalByPlayerId(Long playerId);
}
