package com.mlbgame.season.earnings;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.mode.LeagueRuleset;
import com.mlbgame.league.mode.LeagueRulesets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수령 원장 기록. 커리어 누적 연봉(M5 랭킹) = 원장 합계. 설계: docs/league-world.md 8.3
 */
@Service
public class EarningsService {

    private final PlayerEarningRepository repository;

    public EarningsService(PlayerEarningRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void add(Long playerId, int seasonYear, EarningType type, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerEarning earning = repository.findByPlayerIdAndSeasonYearAndType(playerId, seasonYear, type)
                .orElseGet(() -> repository.save(new PlayerEarning(playerId, seasonYear, type)));
        earning.add(amount);
    }

    /** 시리즈 1회분 연봉 = 소속 레벨 시즌 연봉 / 시즌 시리즈 수(MLB 54) (마이너는 레벨별 연봉, 메이저는 최저 연봉 — 계약 연봉은 M4) */
    public static long salaryPerSeries(LeagueLevel level) {
        LeagueRuleset rules = LeagueRulesets.of(level);
        return Math.round((double) rules.payroll().levelSalary(level) / rules.seriesPerSeason());
    }

    @Transactional(readOnly = true)
    public long careerTotal(Long playerId) {
        return repository.totalByPlayerId(playerId);
    }
}
