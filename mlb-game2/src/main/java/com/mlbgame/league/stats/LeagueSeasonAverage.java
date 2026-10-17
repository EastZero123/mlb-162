package com.mlbgame.league.stats;

import com.mlbgame.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 시즌 전환 직전에 남긴 그해 메이저리그 NPC 기록 합계 ({@link LeagueAverages} 의 저장 형태) */
@Entity
@Table(name = "league_season_averages")
public class LeagueSeasonAverage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "plate_appearances", nullable = false)
    private long plateAppearances;

    @Column(name = "at_bats", nullable = false)
    private long atBats;

    @Column(nullable = false)
    private long hits;

    @Column(nullable = false)
    private long doubles;

    @Column(nullable = false)
    private long triples;

    @Column(name = "home_runs", nullable = false)
    private long homeRuns;

    @Column(nullable = false)
    private long walks;

    @Column(name = "hit_by_pitch", nullable = false)
    private long hitByPitch;

    @Column(name = "sac_flies", nullable = false)
    private long sacFlies;

    @Column(nullable = false)
    private long runs;

    @Column(name = "pitching_outs", nullable = false)
    private long pitchingOuts;

    @Column(name = "pitching_runs", nullable = false)
    private long pitchingRuns;

    @Column(name = "pitching_home_runs", nullable = false)
    private long pitchingHomeRuns;

    @Column(name = "pitching_walks", nullable = false)
    private long pitchingWalks;

    @Column(name = "pitching_strikeouts", nullable = false)
    private long pitchingStrikeouts;

    protected LeagueSeasonAverage() {
    }

    public LeagueSeasonAverage(Long worldId, int seasonYear, LeagueAverages a) {
        this.worldId = worldId;
        this.seasonYear = seasonYear;
        this.plateAppearances = a.plateAppearances();
        this.atBats = a.atBats();
        this.hits = a.hits();
        this.doubles = a.doubles();
        this.triples = a.triples();
        this.homeRuns = a.homeRuns();
        this.walks = a.walks();
        this.hitByPitch = a.hitByPitch();
        this.sacFlies = a.sacFlies();
        this.runs = a.runs();
        this.pitchingOuts = a.pitchingOuts();
        this.pitchingRuns = a.pitchingRuns();
        this.pitchingHomeRuns = a.pitchingHomeRuns();
        this.pitchingWalks = a.pitchingWalks();
        this.pitchingStrikeouts = a.pitchingStrikeouts();
    }

    public LeagueAverages toAverages() {
        return new LeagueAverages(plateAppearances, atBats, hits, doubles, triples, homeRuns, walks, hitByPitch,
                sacFlies, runs, pitchingOuts, pitchingRuns, pitchingHomeRuns, pitchingWalks, pitchingStrikeouts);
    }

    public Long getWorldId() {
        return worldId;
    }

    public int getSeasonYear() {
        return seasonYear;
    }
}
