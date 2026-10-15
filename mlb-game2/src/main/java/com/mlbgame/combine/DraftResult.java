package com.mlbgame.combine;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 드래프트 지명 결과 (선수당 1건) */
@Entity
@Table(name = "draft_results")
public class DraftResult extends BaseTimeEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "draft_round", nullable = false)
    private int round;

    @Column(name = "pick_in_round", nullable = false)
    private int pickInRound;

    @Column(name = "overall_pick", nullable = false)
    private int overallPick;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "signing_bonus", nullable = false)
    private long signingBonus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "start_level", nullable = false, length = 10)
    private LeagueLevel startLevel;

    protected DraftResult() {
    }

    public DraftResult(Long playerId, int round, int pickInRound, int overallPick, Team team,
                       long signingBonus, LeagueLevel startLevel) {
        this.playerId = playerId;
        this.round = round;
        this.pickInRound = pickInRound;
        this.overallPick = overallPick;
        this.team = team;
        this.signingBonus = signingBonus;
        this.startLevel = startLevel;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getRound() {
        return round;
    }

    public int getPickInRound() {
        return pickInRound;
    }

    public int getOverallPick() {
        return overallPick;
    }

    public Team getTeam() {
        return team;
    }

    public long getSigningBonus() {
        return signingBonus;
    }

    public LeagueLevel getStartLevel() {
        return startLevel;
    }
}
