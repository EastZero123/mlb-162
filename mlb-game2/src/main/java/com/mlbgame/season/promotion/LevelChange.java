package com.mlbgame.season.promotion;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.league.LeagueLevel;
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

/** 승격·강등 이력 1건 */
@Entity
@Table(name = "level_changes")
public class LevelChange extends BaseTimeEntity {

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
    @Column(name = "from_level", nullable = false, length = 10)
    private LeagueLevel fromLevel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_level", nullable = false, length = 10)
    private LeagueLevel toLevel;

    @Column(nullable = false)
    private double score;

    protected LevelChange() {
    }

    public LevelChange(Long playerId, int seasonYear, int afterSeries, LeagueLevel fromLevel, LeagueLevel toLevel,
                       double score) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.afterSeries = afterSeries;
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.score = score;
    }

    public boolean isPromotion() {
        return toLevel.ordinal() > fromLevel.ordinal();
    }

    /** "더블A 승격" / "싱글A 강등" / 메이저면 "메이저리그 콜업!" */
    public String getMessage() {
        if (isPromotion()) {
            return toLevel == LeagueLevel.MLB ? "메이저리그 콜업!" : toLevel.getLabel() + " 승격!";
        }
        return toLevel.getLabel() + " 강등";
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

    public LeagueLevel getFromLevel() {
        return fromLevel;
    }

    public LeagueLevel getToLevel() {
        return toLevel;
    }

    public double getScore() {
        return score;
    }
}
