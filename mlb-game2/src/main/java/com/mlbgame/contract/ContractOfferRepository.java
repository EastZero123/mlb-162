package com.mlbgame.contract;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractOfferRepository extends JpaRepository<ContractOffer, Long> {

    List<ContractOffer> findByPlayerIdAndStatusOrderByIdAsc(Long playerId, ContractOffer.Status status);

    List<ContractOffer> findByPlayerIdOrderByIdDesc(Long playerId);

    boolean existsByPlayerIdAndStatus(Long playerId, ContractOffer.Status status);
}
