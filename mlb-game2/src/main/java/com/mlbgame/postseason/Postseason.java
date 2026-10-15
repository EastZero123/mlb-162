package com.mlbgame.postseason;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.simulation.rules.PitcherUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 포스트시즌 진행 상태 (선수·연도당 1건) */
@Entity
@Table(name = "postseasons")
public class Postseason extends BaseTimeEntity {

    public static final int DONE_ROUND = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "next_round", nullable = false)
    private int nextRound;

    @Column(name = "user_team_qualified", nullable = false)
    private boolean userTeamQualified;

    @Column(name = "user_plays", nullable = false)
    private boolean userPlays;

    @Column(name = "bracket_json", nullable = false, length = 8000)
    private String bracketJson;

    @Column(name = "batting_json", length = 2000)
    private String battingJson;

    @Column(name = "pitching_json", length = 2000)
    private String pitchingJson;

    @Column(nullable = false)
    private int fatigue;

    @Column(name = "games_since_start", nullable = false)
    private int gamesSinceStart;

    @Column(name = "consecutive_days", nullable = false)
    private int consecutiveDays;

    @Column(name = "last_pitches", nullable = false)
    private int lastPitches;

    @Column(name = "games_since_appearance", nullable = false)
    private int gamesSinceAppearance;

    @Column(nullable = false)
    private boolean injured;

    @Column(name = "champion_team_id")
    private Long championTeamId;

    @Version
    private Long version;

    protected Postseason() {
    }

    public Postseason(Long playerId, int seasonYear, boolean userTeamQualified, boolean userPlays, String bracketJson,
                      int fatigue, PitcherUsage usage) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.status = "IN_PROGRESS";
        this.nextRound = 1;
        this.userTeamQualified = userTeamQualified;
        this.userPlays = userPlays;
        this.bracketJson = bracketJson;
        this.fatigue = fatigue;
        updateUsage(usage);
    }

    public PitcherUsage usage() {
        return new PitcherUsage(gamesSinceStart, consecutiveDays, lastPitches, gamesSinceAppearance);
    }

    void updateUsage(PitcherUsage usage) {
        this.gamesSinceStart = usage.gamesSinceStart();
        this.consecutiveDays = usage.consecutiveDays();
        this.lastPitches = usage.lastPitches();
        this.gamesSinceAppearance = usage.gamesSinceAppearance();
    }

    void afterRound(String bracketJson, String battingJson, String pitchingJson, int fatigue, boolean injured) {
        this.bracketJson = bracketJson;
        this.battingJson = battingJson;
        this.pitchingJson = pitchingJson;
        this.fatigue = fatigue;
        this.injured = this.injured || injured;
        nextRound++;
    }

    void finish(Long championTeamId) {
        this.championTeamId = championTeamId;
        this.status = "DONE";
        this.nextRound = DONE_ROUND;
    }

    public boolean isDone() {
        return "DONE".equals(status);
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

    public String getStatus() {
        return status;
    }

    public int getNextRound() {
        return nextRound;
    }

    public boolean isUserTeamQualified() {
        return userTeamQualified;
    }

    public boolean isUserPlays() {
        return userPlays;
    }

    public String getBracketJson() {
        return bracketJson;
    }

    public String getBattingJson() {
        return battingJson;
    }

    public String getPitchingJson() {
        return pitchingJson;
    }

    public int getFatigue() {
        return fatigue;
    }

    public boolean isInjured() {
        return injured;
    }

    public Long getChampionTeamId() {
        return championTeamId;
    }
}
