package com.mlbgame.league.world;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NpcPlayerRepository extends JpaRepository<NpcPlayer, Long> {

    List<NpcPlayer> findByWorldIdAndTeamIdAndStatus(Long worldId, Long teamId, NpcStatus status);

    List<NpcPlayer> findByWorldIdAndStatus(Long worldId, NpcStatus status);

    long countByWorldId(Long worldId);

    List<NpcPlayer> findByWorldIdAndStatusIn(Long worldId, Collection<NpcStatus> statuses);
}
