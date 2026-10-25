package com.mlbgame.player;

import com.mlbgame.league.LeagueMode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByUserIdAndStatusOrderByIdAsc(Long userId, PlayerStatus status);

    /** 한 모드의 선수 (대시보드는 모드별로 보여준다) */
    List<Player> findByUserIdAndStatusAndLeagueModeOrderByIdAsc(Long userId, PlayerStatus status, LeagueMode leagueMode);

    long countByUserIdAndStatus(Long userId, PlayerStatus status);

    /** 슬롯은 모드별 */
    long countByUserIdAndStatusAndLeagueMode(Long userId, PlayerStatus status, LeagueMode leagueMode);

    Optional<Player> findByIdAndUserId(Long id, Long userId);
}
