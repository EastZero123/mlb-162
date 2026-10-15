package com.mlbgame.trade;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeEventRepository extends JpaRepository<TradeEvent, Long> {

    List<TradeEvent> findByPlayerIdAndSeasonYearOrderByIdAsc(Long playerId, int seasonYear);

    List<TradeEvent> findByPlayerIdOrderByIdAsc(Long playerId);

    List<TradeEvent> findByPlayerIdAndSeasonYearAndAfterSeries(Long playerId, int seasonYear, int afterSeries);
}
