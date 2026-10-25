package com.mlbgame.season;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.common.Messages;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.simulation.rules.PitcherUsage;
import com.mlbgame.simulation.series.SeriesResult;
import com.mlbgame.simulation.series.SeriesSimulator.CarryOver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 내 선수의 한 시즌 진행 상태와 누적 기록. 시즌 기록(타격·투구·수비)은 JSON 으로 보관한다.
 */
@Entity
@Table(name = "player_seasons")
public class PlayerSeason extends BaseTimeEntity {

    /** MLB 정규시즌 시리즈 수 — 모드별 값은 {@code LeagueRuleset.seriesPerSeason()} */
    public static final int SERIES_PER_SEASON = 54;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "league_level", nullable = false, length = 10)
    private LeagueLevel leagueLevel;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "next_series_no", nullable = false)
    private int nextSeriesNo;

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

    @Column(name = "injured_series_remaining", nullable = false)
    private int injuredSeriesRemaining;

    @Column(name = "injury_severity", length = 20)
    private String injurySeverity;

    @Column(name = "training_pending", nullable = false)
    private boolean trainingPending;

    @Column(name = "team_wins", nullable = false)
    private int teamWins;

    @Column(name = "team_losses", nullable = false)
    private int teamLosses;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SeasonStatus status;

    @Column(name = "batting_json", length = 2000)
    private String battingJson;

    @Column(name = "pitching_json", length = 2000)
    private String pitchingJson;

    @Column(name = "fielding_json", length = 2000)
    private String fieldingJson;

    @Column(name = "mlb_series_played", nullable = false)
    private int mlbSeriesPlayed;

    @Version
    private Long version;

    protected PlayerSeason() {
    }

    public static PlayerSeason start(Long playerId, int year, LeagueLevel level, Long teamId) {
        PlayerSeason season = new PlayerSeason();
        season.playerId = playerId;
        season.seasonYear = year;
        season.leagueLevel = level;
        season.teamId = teamId;
        season.nextSeriesNo = 1;
        PitcherUsage usage = PitcherUsage.seasonStart();
        season.gamesSinceStart = usage.gamesSinceStart();
        season.consecutiveDays = usage.consecutiveDays();
        season.lastPitches = usage.lastPitches();
        season.gamesSinceAppearance = usage.gamesSinceAppearance();
        season.status = SeasonStatus.IN_PROGRESS;
        return season;
    }

    public PitcherUsage usage() {
        return new PitcherUsage(gamesSinceStart, consecutiveDays, lastPitches, gamesSinceAppearance);
    }

    public boolean isCompleted() {
        return status == SeasonStatus.COMPLETED;
    }

    public boolean isSidelined() {
        return injuredSeriesRemaining > 0;
    }

    /**
     * 시리즈 결과 반영. 기록 JSON 은 서비스가 합산해서 넘긴다.
     *
     * @param sidelined       부상으로 결장한 시리즈였는지
     * @param seriesPerSeason 모드의 정규시즌 시리즈 수 (시즌 아웃 부상 기간·시즌 종료 판정)
     */
    void applySeries(SeriesResult result, CarryOver next, boolean sidelined, int seriesPerSeason,
                     String battingJson, String pitchingJson, String fieldingJson) {
        teamWins += result.wins();
        teamLosses += result.losses();
        if (leagueLevel.isTop()) {                  // 최상위 레벨 시리즈 수 (서비스 연수·주요 시즌)
            mlbSeriesPlayed++;
        }
        fatigue = next.fatigue();
        gamesSinceStart = next.usage().gamesSinceStart();
        consecutiveDays = next.usage().consecutiveDays();
        lastPitches = next.usage().lastPitches();
        gamesSinceAppearance = next.usage().gamesSinceAppearance();
        this.battingJson = battingJson;
        this.pitchingJson = pitchingJson;
        this.fieldingJson = fieldingJson;

        if (sidelined) {
            injuredSeriesRemaining--;
            if (injuredSeriesRemaining == 0) {
                injurySeverity = null;
            }
        }
        if (result.injury() != null) {
            injurySeverity = result.injury().severity().name();
            injuredSeriesRemaining = result.injury().isSeasonEnding()
                    ? seriesPerSeason
                    : result.injury().seriesOut();
        }

        nextSeriesNo++;
        trainingPending = true;
        if (nextSeriesNo > seriesPerSeason) {
            status = SeasonStatus.COMPLETED;
        }
    }

    /** 훈련을 마쳐 다음 시리즈를 진행할 수 있게 함 — {@code TrainingService}, 능력치가 모두 100이면 {@code SeasonService} */
    public void completeTraining() {
        if (!trainingPending) {
            throw new SeasonProgressException(Messages.get("season.error.notTrainingTime"));
        }
        trainingPending = false;
    }

    /** FA 이적: 시즌을 시작하기 전(1번 시리즈 전)에만 소속 구단 변경 */
    public void relocate(Long teamId) {
        if (nextSeriesNo != 1) {
            throw new SeasonProgressException(Messages.get("season.error.relocateMidSeason"));
        }
        this.teamId = teamId;
    }

    /** 시즌 중 트레이드: 다음 시리즈부터 새 구단 일정으로 */
    public void trade(Long teamId) {
        this.teamId = teamId;
    }

    /** 승격·강등 (다음 시리즈부터 새 레벨) */
    void changeLevel(LeagueLevel level) {
        this.leagueLevel = level;
    }

    public int getMlbSeriesPlayed() {
        return mlbSeriesPlayed;
    }

    /** 오프시즌 회복: 시즌이 끝나 다음 시즌으로 넘어가면 남은 부상 결장은 모두 지운다 */
    public void recoverFromInjury() {
        this.injurySeverity = null;
        this.injuredSeriesRemaining = 0;
    }

    /** 테스트·관리용: 부상 결장 시리즈를 직접 지정 */
    public void injure(String severity, int seriesOut) {
        this.injurySeverity = severity;
        this.injuredSeriesRemaining = seriesOut;
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

    public LeagueLevel getLeagueLevel() {
        return leagueLevel;
    }

    public Long getTeamId() {
        return teamId;
    }

    public int getNextSeriesNo() {
        return nextSeriesNo;
    }

    public int getSeriesPlayed() {
        return nextSeriesNo - 1;
    }

    public int getFatigue() {
        return fatigue;
    }

    public int getInjuredSeriesRemaining() {
        return injuredSeriesRemaining;
    }

    public String getInjurySeverity() {
        return injurySeverity;
    }

    public boolean isTrainingPending() {
        return trainingPending;
    }

    public int getTeamWins() {
        return teamWins;
    }

    public int getTeamLosses() {
        return teamLosses;
    }

    public SeasonStatus getStatus() {
        return status;
    }

    public String getBattingJson() {
        return battingJson;
    }

    public String getPitchingJson() {
        return pitchingJson;
    }

    public String getFieldingJson() {
        return fieldingJson;
    }
}
