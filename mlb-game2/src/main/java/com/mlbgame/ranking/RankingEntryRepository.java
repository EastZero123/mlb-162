package com.mlbgame.ranking;

import com.mlbgame.league.LeagueMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 랭킹은 모드별로 따로 본다 (MLB 선수와 KBO 선수를 섞지 않음) */
public interface RankingEntryRepository extends JpaRepository<RankingEntry, Long> {

    Optional<RankingEntry> findByPlayerId(Long playerId);

    boolean existsByPlayerId(Long playerId);

    List<RankingEntry> findByLeagueModeAndRetiredAtGreaterThanEqual(LeagueMode leagueMode, LocalDateTime from,
                                                                   Pageable pageable);

    List<RankingEntry> findByLeagueModeAndRetiredAtGreaterThanEqualAndMlbPaGreaterThanEqual(
            LeagueMode leagueMode, LocalDateTime from, int minPa, Pageable pageable);

    List<RankingEntry> findByLeagueModeAndRetiredAtGreaterThanEqualAndMlbOutsGreaterThanEqual(
            LeagueMode leagueMode, LocalDateTime from, int minOuts, Pageable pageable);
}
