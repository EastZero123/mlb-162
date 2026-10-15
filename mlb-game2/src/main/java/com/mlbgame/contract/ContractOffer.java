package com.mlbgame.contract;

import com.mlbgame.common.BaseTimeEntity;
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

/** 오프시즌 계약 제안 1건 */
@Entity
@Table(name = "contract_offers")
public class ContractOffer extends BaseTimeEntity {

    public enum Kind {
        ARBITRATION("연봉조정"),
        EXTENSION("연장 계약 제안"),
        FREE_AGENCY("FA 오퍼");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Status {PENDING, ACCEPTED, REJECTED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private int years;

    @Column(nullable = false)
    private long salary;

    @Column(name = "fair_value", nullable = false)
    private long fairValue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 255)
    private String note;

    protected ContractOffer() {
    }

    public ContractOffer(Long playerId, int seasonYear, Kind kind, Team team, int years, long salary, long fairValue) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.kind = kind;
        this.team = team;
        this.years = years;
        this.salary = salary;
        this.fairValue = fairValue;
        this.status = Status.PENDING;
    }

    void accept(long finalSalary, String note) {
        this.salary = finalSalary;
        this.status = Status.ACCEPTED;
        this.note = note;
    }

    void reject(String note) {
        this.status = Status.REJECTED;
        this.note = note;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public long getTotalValue() {
        return salary * years;
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public Kind getKind() {
        return kind;
    }

    public Team getTeam() {
        return team;
    }

    public int getYears() {
        return years;
    }

    public long getSalary() {
        return salary;
    }

    long getFairValue() {
        return fairValue;
    }

    public Status getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }
}
