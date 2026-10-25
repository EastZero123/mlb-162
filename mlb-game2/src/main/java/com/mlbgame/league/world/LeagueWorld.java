package com.mlbgame.league.world;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.league.LeagueMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "league_mode", nullable = false, length = 5)
    private LeagueMode leagueMode = LeagueMode.MLB;

    protected LeagueWorld() {
    }

    public LeagueWorld(Long playerId, long seed, int currentYear) {
        this(playerId, seed, currentYear, LeagueMode.MLB);
    }

    public LeagueWorld(Long playerId, long seed, int currentYear, LeagueMode leagueMode) {
        this.playerId = playerId;
        this.seed = seed;
        this.currentYear = currentYear;
        this.leagueMode = leagueMode;
    }

    public LeagueMode getLeagueMode() {
        return leagueMode;
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
