package com.mlbgame.league.world;

import com.mlbgame.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 내 선수 커리어 전용 리그 월드 */
@Entity
@Table(name = "league_worlds")
public class LeagueWorld extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    @Column(nullable = false)
    private long seed;

    @Column(name = "current_year", nullable = false)
    private int currentYear;

    protected LeagueWorld() {
    }

    public LeagueWorld(Long playerId, long seed, int currentYear) {
        this.playerId = playerId;
        this.seed = seed;
        this.currentYear = currentYear;
    }

    /** 다음 시즌으로 */
    public void advanceYear() {
        this.currentYear++;
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public long getSeed() {
        return seed;
    }

    public int getCurrentYear() {
        return currentYear;
    }
}
