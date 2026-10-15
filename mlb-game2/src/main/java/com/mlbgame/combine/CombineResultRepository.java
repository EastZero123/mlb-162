package com.mlbgame.combine;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CombineResultRepository extends JpaRepository<CombineResult, Long> {

    List<CombineResult> findByPlayerIdOrderByIdAsc(Long playerId);
}
