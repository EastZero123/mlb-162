package com.mlbgame.season;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.stats.AdvancedStats;
import com.mlbgame.league.stats.LeagueAverageService;
import com.mlbgame.league.stats.LeagueAverages;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.player.Position;
import com.mlbgame.season.SeasonSplits.Kind;
import com.mlbgame.season.SeasonSplits.Row;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/**
 * 기록표 행마다 세부 지표를 붙인다 (세부 지표 표 — 커리어 화면·시즌 기록 화면 공용).
 *
 * <ul>
 *   <li>리그 평균이 필요 없는 지표(ISO·BABIP·BB%·K%·wOBA, 투수 K%·BB%·K−BB%)는 메이저·마이너 모두, 합계 행은 합친 기록으로</li>
 *   <li>리그 평균이 필요한 지표(wRC+·OPS+·FIP·ERA+·FIP−·WAR)는 메이저 행만. 마이너는 리그 기록이 없어 null("-")</li>
 *   <li>합계·통산 행: WAR 는 각 구간 합, wRC+·OPS+ 는 타석 가중 평균, FIP·ERA+·FIP− 는 이닝 가중 평균
 *       (해마다 리그 평균이 달라 합친 기록으로 다시 계산하지 않는다)</li>
 * </ul>
 */
@Component
public class SeasonMetrics {

    private final LeagueAverageService leagueAverageService;

    public SeasonMetrics(LeagueAverageService leagueAverageService) {
        this.leagueAverageService = leagueAverageService;
    }

    /** 세부 지표 표 한 줄. 타자면 hitter, 투수면 pitcher 만 채운다 */
    public record DetailRow(Row row, AdvancedStats.Hitter hitter, AdvancedStats.Pitcher pitcher) {
    }

    public record DetailTables(List<DetailRow> mlb, List<DetailRow> minors) {
    }

    /**
     * @param world             내 선수의 리그 월드 (없으면 기준 리그 평균)
     * @param seriesPlayedByYear 연도 → 그 시즌 진행한 시리즈 수 (진행 중인 시즌의 리그 평균 즉석 계산용)
     */
    public DetailTables detail(SeasonSplits.Tables tables, Position position, boolean pitcher, LeagueWorld world,
                               Map<Integer, Integer> seriesPlayedByYear) {
        Map<Integer, LeagueAverages> cache = new HashMap<>();
        LeagueByYear mlbLeague = year -> cache.computeIfAbsent(year,
                y -> leagueAverageService.forYear(world, y, seriesPlayedByYear.getOrDefault(y, 0)));
        return new DetailTables(
                rows(tables.mlb(), position, pitcher, mlbLeague),
                rows(tables.minors(), position, pitcher, year -> null));
    }

    @FunctionalInterface
    interface LeagueByYear {
        LeagueAverages of(int year);
    }

    static List<DetailRow> rows(List<Row> rows, Position position, boolean pitcher, LeagueByYear league) {
        List<DetailRow> result = new ArrayList<>();
        List<DetailRow> yearSplits = new ArrayList<>();
        List<DetailRow> allSplits = new ArrayList<>();
        Integer currentYear = null;
        for (Row row : rows) {
            DetailRow detail;
            if (row.kind() == Kind.SPLIT) {
                if (!row.year().equals(currentYear)) {
                    yearSplits.clear();
                    currentYear = row.year();
                }
                LeagueAverages lg = row.level() == LeagueLevel.MLB ? league.of(row.year()) : null;
                detail = pitcher
                        ? new DetailRow(row, null, AdvancedStats.pitcher(row.pitching(), lg))
                        : new DetailRow(row, AdvancedStats.hitter(row.batting(), row.fielding(), position, lg), null);
                yearSplits.add(detail);
                allSplits.add(detail);
            } else {
                List<DetailRow> covered = row.kind() == Kind.YEAR_TOTAL ? List.copyOf(yearSplits) : List.copyOf(allSplits);
                detail = pitcher ? new DetailRow(row, null, pitcherTotal(row, covered))
                        : new DetailRow(row, hitterTotal(row, position, covered), null);
            }
            result.add(detail);
        }
        return result;
    }

    private static AdvancedStats.Hitter hitterTotal(Row row, Position position, List<DetailRow> covered) {
        AdvancedStats.Hitter base = AdvancedStats.hitter(row.batting(), row.fielding(), position, null);
        ToDoubleFunction<DetailRow> pa = d -> d.row().batting().plateAppearances;
        return new AdvancedStats.Hitter(base.iso(), base.babip(), base.walkRate(), base.strikeoutRate(), base.woba(),
                weighted(covered, d -> d.hitter().wrcPlus(), pa),
                weighted(covered, d -> d.hitter().opsPlus(), pa),
                sum(covered, d -> d.hitter().war()));
    }

    private static AdvancedStats.Pitcher pitcherTotal(Row row, List<DetailRow> covered) {
        AdvancedStats.Pitcher base = AdvancedStats.pitcher(row.pitching(), null);
        ToDoubleFunction<DetailRow> outs = d -> d.row().pitching().outs;
        return new AdvancedStats.Pitcher(base.strikeoutRate(), base.walkRate(), base.kMinusBb(),
                weighted(covered, d -> d.pitcher().fip(), outs),
                weighted(covered, d -> d.pitcher().eraPlus(), outs),
                weighted(covered, d -> d.pitcher().fipMinus(), outs),
                sum(covered, d -> d.pitcher().war()));
    }

    interface Metric {
        Double of(DetailRow row);
    }

    /** 가중 평균 — 구간 중 하나라도 값이 없으면(마이너 등) null */
    private static Double weighted(List<DetailRow> rows, Metric metric, ToDoubleFunction<DetailRow> weight) {
        double total = 0;
        double weights = 0;
        for (DetailRow row : rows) {
            Double value = metric.of(row);
            double w = weight.applyAsDouble(row);
            if (w == 0) {
                continue;
            }
            if (value == null) {
                return null;
            }
            total += value * w;
            weights += w;
        }
        return weights == 0 ? null : total / weights;
    }

    private static Double sum(List<DetailRow> rows, Metric metric) {
        double total = 0;
        for (DetailRow row : rows) {
            Double value = metric.of(row);
            if (value == null) {
                return null;
            }
            total += value;
        }
        return rows.isEmpty() ? null : total;
    }
}
