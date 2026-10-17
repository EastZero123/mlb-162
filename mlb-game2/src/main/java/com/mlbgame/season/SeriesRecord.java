package com.mlbgame.season;

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

/**
 * 진행한 시리즈 한 건. 상세 내용은 저장하지 않고 {@code inputJson}(시드 포함)으로 다시 계산한다.
 */
@Entity
@Table(name = "series_records")
public class SeriesRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "series_no", nullable = false)
    private int seriesNo;

    @Column(nullable = false)
    private long seed;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "league_level", nullable = false, length = 10)
    private LeagueLevel leagueLevel;

    /** 이 시리즈를 뛴 소속 구단 (시즌 중 트레이드되면 시리즈마다 다를 수 있다) */
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "input_json", nullable = false, length = 4000)
    private String inputJson;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(name = "summary_json", nullable = false, length = 4000)
    private String summaryJson;

    protected SeriesRecord() {
    }

    public SeriesRecord(Long playerId, int seasonYear, int seriesNo, long seed, LeagueLevel leagueLevel, Long teamId,
                        String inputJson, int wins, int losses, String summaryJson) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.seriesNo = seriesNo;
        this.seed = seed;
        this.leagueLevel = leagueLevel;
        this.teamId = teamId;
        this.inputJson = inputJson;
        this.wins = wins;
        this.losses = losses;
        this.summaryJson = summaryJson;
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

    public int getSeriesNo() {
        return seriesNo;
    }

    public long getSeed() {
        return seed;
    }

    public LeagueLevel getLeagueLevel() {
        return leagueLevel;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getInputJson() {
        return inputJson;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public String getSummaryJson() {
        return summaryJson;
    }
}
