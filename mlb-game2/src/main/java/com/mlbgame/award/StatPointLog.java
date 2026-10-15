package com.mlbgame.award;

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

/** 스탯업 포인트 사용 1회 */
@Entity
@Table(name = "stat_point_logs")
public class StatPointLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "attr_code", nullable = false, length = 30)
    private Attribute attribute;

    @Column(name = "value_after", nullable = false)
    private int valueAfter;

    protected StatPointLog() {
    }

    public StatPointLog(Long playerId, Attribute attribute, int valueAfter) {
        this.playerId = playerId;
        this.attribute = attribute;
        this.valueAfter = valueAfter;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public int getValueAfter() {
        return valueAfter;
    }
}
