package com.mlbgame.reputation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReputationLogRepository extends JpaRepository<ReputationLog, Long> {

    List<ReputationLog> findTop5ByPlayerIdOrderByIdDesc(Long playerId);

    List<ReputationLog> findByPlayerIdOrderByIdAsc(Long playerId);
}
