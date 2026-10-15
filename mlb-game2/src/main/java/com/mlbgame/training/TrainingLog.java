package com.mlbgame.training;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.player.Attribute;
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

/** 훈련 1회 기록 (시리즈당 1건) */
@Entity
@Table(name = "training_logs")
public class TrainingLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "series_no", nullable = false)
    private int seriesNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "attr_code", nullable = false, length = 30)
    private Attribute attribute;

    @Column(name = "value_after", nullable = false)
    private int valueAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private TrainingSource source;

    protected TrainingLog() {
    }

    public TrainingLog(Long playerId, int seasonYear, int seriesNo, Attribute attribute, int valueAfter,
                       TrainingSource source) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.seriesNo = seriesNo;
        this.attribute = attribute;
        this.valueAfter = valueAfter;
        this.source = source;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public int getSeriesNo() {
        return seriesNo;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public int getValueAfter() {
        return valueAfter;
    }

    public TrainingSource getSource() {
        return source;
    }
}
