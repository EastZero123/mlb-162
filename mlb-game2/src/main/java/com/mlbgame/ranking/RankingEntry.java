package com.mlbgame.ranking;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.player.Position;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 은퇴 선수 커리어 스냅샷 (랭킹 1행) */
@Entity
@Table(name = "ranking_entries")
public class RankingEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "player_name", nullable = false, length = 20)
    private String playerName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Position position;

    @Column(name = "final_age", nullable = false)
    private int finalAge;

    @Column(nullable = false)
    private int seasons;

    @Column(name = "retired_at", nullable = false)
    private LocalDateTime retiredAt;

    @Column(name = "career_earnings", nullable = false)
    private long careerEarnings;

    @Column(name = "hall_of_fame", nullable = false)
    private int hallOfFame;

    @Column(name = "award_points", nullable = false)
    private int awardPoints;

    @Column(name = "mlb_games", nullable = false)
    private int mlbGames;

    @Column(name = "mlb_pa", nullable = false)
    private int mlbPa;

    @Column(name = "mlb_hits", nullable = false)
    private int mlbHits;

    @Column(name = "mlb_home_runs", nullable = false)
    private int mlbHomeRuns;

    @Column(name = "mlb_avg", nullable = false)
    private double mlbAvg;

    @Column(name = "mlb_outs", nullable = false)
    private int mlbOuts;

    @Column(name = "mlb_wins", nullable = false)
    private int mlbWins;

    @Column(name = "mlb_strikeouts", nullable = false)
    private int mlbStrikeouts;

    @Column(name = "mlb_saves", nullable = false)
    private int mlbSaves;

    @Column(name = "mlb_era", nullable = false)
    private double mlbEra;

    @Column(name = "combine_score", nullable = false)
    private double combineScore;

    protected RankingEntry() {
    }

    /** 스냅샷 빌더 — 필드가 많아 단계별로 채운다 */
    public static RankingEntry of(Long playerId, Long userId, String nickname, String playerName, Position position,
                                  int finalAge, int seasons, LocalDateTime retiredAt) {
        RankingEntry e = new RankingEntry();
        e.playerId = playerId;
        e.userId = userId;
        e.nickname = nickname;
        e.playerName = playerName;
        e.position = position;
        e.finalAge = finalAge;
        e.seasons = seasons;
        e.retiredAt = retiredAt;
        return e;
    }

    public RankingEntry career(long earnings, int hallOfFame, int awardPoints, double combineScore) {
        this.careerEarnings = earnings;
        this.hallOfFame = hallOfFame;
        this.awardPoints = awardPoints;
        this.combineScore = combineScore;
        return this;
    }

    public RankingEntry batting(int games, int pa, int hits, int homeRuns, double avg) {
        this.mlbGames = games;
        this.mlbPa = pa;
        this.mlbHits = hits;
        this.mlbHomeRuns = homeRuns;
        this.mlbAvg = avg;
        return this;
    }

    public RankingEntry pitching(int games, int outs, int wins, int strikeouts, int saves, double era) {
        this.mlbGames = games;
        this.mlbOuts = outs;
        this.mlbWins = wins;
        this.mlbStrikeouts = strikeouts;
        this.mlbSaves = saves;
        this.mlbEra = era;
        return this;
    }

    public Long getId() { return id; }
    public Long getPlayerId() { return playerId; }
    public Long getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public String getPlayerName() { return playerName; }
    public Position getPosition() { return position; }
    public int getFinalAge() { return finalAge; }
    public int getSeasons() { return seasons; }
    public LocalDateTime getRetiredAt() { return retiredAt; }
    public long getCareerEarnings() { return careerEarnings; }
    public int getHallOfFame() { return hallOfFame; }
    public int getAwardPoints() { return awardPoints; }
    public int getMlbGames() { return mlbGames; }
    public int getMlbPa() { return mlbPa; }
    public int getMlbHits() { return mlbHits; }
    public int getMlbHomeRuns() { return mlbHomeRuns; }
    public double getMlbAvg() { return mlbAvg; }
    public int getMlbOuts() { return mlbOuts; }
    public int getMlbWins() { return mlbWins; }
    public int getMlbStrikeouts() { return mlbStrikeouts; }
    public int getMlbSaves() { return mlbSaves; }
    public double getMlbEra() { return mlbEra; }
    public double getCombineScore() { return combineScore; }
}
