package com.mlbgame.league.stats;

import com.mlbgame.league.stats.LeagueStatsService.PlayerLine;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.season.schedule.ScheduledSeries;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌별 메이저리그 평균. 우선순위:
 * <ol>
 *   <li>저장된 값 (시즌 전환 직전에 {@link #snapshot} 으로 남긴 그해 전체 합계)</li>
 *   <li>진행 중인 시즌이면 지금까지 끝난 달의 NPC 기록으로 즉석 계산 (시즌 초반에는 값이 출렁인다)</li>
 *   <li>그 밖(이번 패치 이전에 끝난 시즌, 아직 한 달도 안 지난 시즌) → {@link LeagueAverages#BASELINE}</li>
 * </ol>
 */
@Service
public class LeagueAverageService {

    static final int MONTHS_PER_SEASON = 6;

    private final LeagueStatsService leagueStatsService;
    private final LeagueSeasonAverageRepository repository;

    public LeagueAverageService(LeagueStatsService leagueStatsService, LeagueSeasonAverageRepository repository) {
        this.leagueStatsService = leagueStatsService;
        this.repository = repository;
    }

    /** 시즌 전환 직전: 그해 메이저 NPC 전체 기록 합계를 남긴다 (이미 있으면 그대로) */
    @Transactional
    public LeagueAverages snapshot(LeagueWorld world, int year) {
        return repository.findByWorldIdAndSeasonYear(world.getId(), year)
                .map(LeagueSeasonAverage::toAverages)
                .orElseGet(() -> {
                    LeagueAverages averages = compute(world, year, MONTHS_PER_SEASON);
                    repository.save(new LeagueSeasonAverage(world.getId(), year, averages));
                    return averages;
                });
    }

    /**
     * @param world        내 선수의 리그 월드 (없으면 기준값)
     * @param seriesPlayed 그 시즌 내 선수가 진행한 시리즈 수 (진행 중인 시즌의 즉석 계산용)
     */
    @Transactional(readOnly = true)
    public LeagueAverages forYear(LeagueWorld world, int year, int seriesPlayed) {
        if (world == null) {
            return LeagueAverages.BASELINE;
        }
        return repository.findByWorldIdAndSeasonYear(world.getId(), year)
                .map(LeagueSeasonAverage::toAverages)
                .orElseGet(() -> {
                    int months = Math.min(MONTHS_PER_SEASON, seriesPlayed / ScheduledSeries.SERIES_PER_MONTH);
                    if (year != world.getCurrentYear() || months == 0) {
                        return LeagueAverages.BASELINE;
                    }
                    LeagueAverages live = compute(world, year, months);
                    return live.usable() ? live : LeagueAverages.BASELINE;
                });
    }

    private LeagueAverages compute(LeagueWorld world, int year, int months) {
        BattingLine batting = new BattingLine();
        PitchingLine pitching = new PitchingLine();
        List<PlayerLine> lines = leagueStatsService.lines(world, year, LeagueStatsService.monthsThrough(months));
        for (PlayerLine line : lines) {
            if (line.isPitcher()) {
                pitching.add(line.pitching());
            } else {
                batting.add(line.batting());
            }
        }
        return LeagueAverages.of(batting, pitching);
    }
}
