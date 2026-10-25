package com.mlbgame.league;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** 모든 모드의 구단 — id → 구단 표시용 조회에만 쓴다 (대진·지명·월드 생성은 {@link #findByLeagueModeOrderByIdAsc}) */
    List<Team> findAllByOrderByIdAsc();

    /** 한 모드의 구단 (리그 구성원) */
    List<Team> findByLeagueModeOrderByIdAsc(LeagueMode leagueMode);
}
