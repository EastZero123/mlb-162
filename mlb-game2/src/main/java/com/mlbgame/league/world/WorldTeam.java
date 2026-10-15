package com.mlbgame.league.world;

import com.mlbgame.league.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 월드 안의 구단 성향·재정 */
@Entity
@Table(name = "world_teams")
public class WorldTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Market market;

    @Column(name = "spending_percent", nullable = false)
    private int spendingPercent;

    @Column(name = "farm_score", nullable = false)
    private int farmScore;

    @Column(nullable = false)
    private long budget;

    protected WorldTeam() {
    }

    public WorldTeam(Long worldId, Team team, Market market, int spendingPercent, int farmScore, long budget) {
        this.worldId = worldId;
        this.team = team;
        this.market = market;
        this.spendingPercent = spendingPercent;
        this.farmScore = farmScore;
        this.budget = budget;
    }

    /** 시즌 전환: 예산·팜 점수 갱신 */
    public void updateFinances(long budget, int farmScore) {
        this.budget = budget;
        this.farmScore = farmScore;
    }

    public Long getId() {
        return id;
    }

    public Long getWorldId() {
        return worldId;
    }

    public Team getTeam() {
        return team;
    }

    public Market getMarket() {
        return market;
    }

    public int getSpendingPercent() {
        return spendingPercent;
    }

    public int getFarmScore() {
        return farmScore;
    }

    public long getBudget() {
        return budget;
    }
}
