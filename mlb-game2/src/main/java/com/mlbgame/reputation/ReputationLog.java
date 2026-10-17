package com.mlbgame.reputation;

import com.mlbgame.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 평판 변화 1건 */
@Entity
@Table(name = "reputation_logs")
public class ReputationLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(nullable = false)
    private int delta;

    @Column(name = "reputation_after", nullable = false)
    private int reputationAfter;

    @Column(nullable = false, length = 100)
    private String reason;

    protected ReputationLog() {
    }

    public ReputationLog(Long playerId, int seasonYear, int delta, int reputationAfter, String reason) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.delta = delta;
        this.reputationAfter = reputationAfter;
        this.reason = reason;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public int getDelta() {
        return delta;
    }

    public int getReputationAfter() {
        return reputationAfter;
    }

    public String getReason() {
        return reason;
    }

    /** "+2" / "−8" */
    public String getDeltaLabel() {
        return delta > 0 ? "+" + delta : "−" + Math.abs(delta);
    }
}
