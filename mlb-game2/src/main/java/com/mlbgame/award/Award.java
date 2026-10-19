package com.mlbgame.award;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.common.Messages;
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

/** 수상 1건 */
@Entity
@Table(name = "awards")
public class Award extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "after_series", nullable = false)
    private int afterSeries;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "award_type", nullable = false, length = 30)
    private AwardType type;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private long bonus;

    protected Award() {
    }

    public Award(Long playerId, int seasonYear, int afterSeries, AwardType type) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.afterSeries = afterSeries;
        this.type = type;
        this.points = type.getPoints();
        this.bonus = type.getBonus();
    }

    /** "5월 이달의 선수" / "2026 MVP" */
    public String getTitle() {
        boolean monthly = type.name().endsWith("_OF_MONTH");
        if (monthly) {
            return Messages.get("award.monthlyTitle", Messages.month(3 + afterSeries / 9), type.getLabel());
        }
        return seasonYear + " " + type.getLabel();
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public int getAfterSeries() {
        return afterSeries;
    }

    public AwardType getType() {
        return type;
    }

    public int getPoints() {
        return points;
    }

    public long getBonus() {
        return bonus;
    }
}
