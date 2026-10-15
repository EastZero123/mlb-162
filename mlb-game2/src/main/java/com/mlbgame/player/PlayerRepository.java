package com.mlbgame.player;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByUserIdAndStatusOrderByIdAsc(Long userId, PlayerStatus status);

    long countByUserIdAndStatus(Long userId, PlayerStatus status);

    Optional<Player> findByIdAndUserId(Long id, Long userId);
}
