package com.mlbgame.season.earnings;

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

/** 수령 원장: 선수·연도·종류별로 1행에 금액을 누적한다 */
@Entity
@Table(name = "player_earnings")
public class PlayerEarning extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "earning_type", nullable = false, length = 20)
    private EarningType type;

    @Column(nullable = false)
    private long amount;

    protected PlayerEarning() {
    }

    public PlayerEarning(Long playerId, int seasonYear, EarningType type) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.type = type;
    }

    void add(long value) {
        amount += value;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public EarningType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }
}
