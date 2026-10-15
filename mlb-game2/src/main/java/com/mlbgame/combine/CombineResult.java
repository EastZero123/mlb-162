package com.mlbgame.combine;

import com.mlbgame.common.BaseTimeEntity;
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

/** 컴바인 종목별 기록 */
@Entity
@Table(name = "combine_results")
public class CombineResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "test_type", nullable = false, length = 30)
    private CombineTest testType;

    @Column(nullable = false)
    private int score;

    @Column(name = "record_value", nullable = false)
    private double recordValue;

    protected CombineResult() {
    }

    public CombineResult(Long playerId, CombineTest testType, int score) {
        this.playerId = playerId;
        this.testType = testType;
        this.score = score;
        this.recordValue = testType.recordOf(score);
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public CombineTest getTestType() {
        return testType;
    }

    public int getScore() {
        return score;
    }

    public double getRecordValue() {
        return recordValue;
    }
}
