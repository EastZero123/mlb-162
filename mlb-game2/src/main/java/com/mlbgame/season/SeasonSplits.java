package com.mlbgame.season;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시리즈 기록을 (연도, 레벨, 구단) 단위로 묶은 기록표. 커리어 화면·시즌 기록 화면이 같은 기준을 쓴다.
 *
 * <ul>
 *   <li>같은 해 같은 레벨·같은 구단이면 오간 횟수와 상관없이 한 줄 (AAA → MLB → AAA 면 AAA 한 줄 + MLB 한 줄)</li>
 *   <li>메이저 표와 마이너 표를 나눈다. 합계 행은 메이저에서 한 해에 두 팀 이상 뛰었을 때만</li>
 *   <li>한 번도 출전하지 못한 구간(그 레벨·구단에 있는 내내 부상 결장)은 줄을 만들지 않는다</li>
 * </ul>
 */
@Component
public class SeasonSplits {

    private final SeriesRecordRepository seriesRecordRepository;
    private final SeriesRecorder seriesRecorder;
    private final TeamRepository teamRepository;

    public SeasonSplits(SeriesRecordRepository seriesRecordRepository, SeriesRecorder seriesRecorder,
                        TeamRepository teamRepository) {
        this.seriesRecordRepository = seriesRecordRepository;
        this.seriesRecorder = seriesRecorder;
        this.teamRepository = teamRepository;
    }

    public enum Kind {
        SPLIT,          // 연도·레벨·구단 한 구간
        YEAR_TOTAL,     // 메이저에서 한 해에 여러 팀을 뛴 해의 합계
        CAREER_TOTAL    // 표 맨 아래 통산
    }

    /**
     * 기록표 한 줄. 투수는 fielding 이 비어 있다 (투수 수비 기록은 만들지 않는다).
     *
     * @param teamCount YEAR_TOTAL 일 때 그해 뛴 팀 수
     * @param champion  이 구간 팀으로 시즌을 마치고 월드시리즈 우승
     */
    public record Row(Kind kind, Integer year, LeagueLevel level, Team team, int teamCount, BattingLine batting,
                      PitchingLine pitching, FieldingLine fielding, boolean champion) {

        public boolean isSplit() {
            return kind == Kind.SPLIT;
        }

        public boolean isTotal() {
            return kind != Kind.SPLIT;
        }
    }

    /** 메이저 표·마이너 표 */
    public record Tables(List<Row> mlb, List<Row> minors) {

        public boolean isEmpty() {
            return mlb.isEmpty() && minors.isEmpty();
        }
    }

    /** 커리어 전체 */
    @Transactional(readOnly = true)
    public Tables career(Long playerId, Map<Integer, Long> championTeamByYear) {
        return build(seriesRecordRepository.findByPlayerIdOrderBySeasonYearAscSeriesNoAsc(playerId), championTeamByYear);
    }

    /** 한 시즌 (통산 행 없이) */
    @Transactional(readOnly = true)
    public Tables season(Long playerId, int year) {
        Tables tables = build(seriesRecordRepository.findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(playerId, year), Map.of());
        return new Tables(withoutCareerTotal(tables.mlb()), withoutCareerTotal(tables.minors()));
    }

    Tables build(List<SeriesRecord> records, Map<Integer, Long> championTeamByYear) {
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));

        // 시리즈 순서대로 들어오므로 LinkedHashMap 이면 연도 → 처음 뛴 순서로 정렬된다
        Map<Key, Accumulator> splits = new LinkedHashMap<>();
        for (SeriesRecord record : records) {
            SeriesSummary summary = seriesRecorder.readSummary(record);
            splits.computeIfAbsent(new Key(record.getSeasonYear(), record.getLeagueLevel(), record.getTeamId()),
                    k -> new Accumulator()).add(summary.batting(), summary.pitching(), summary.fielding());
        }

        List<Row> mlb = new ArrayList<>();
        List<Row> minors = new ArrayList<>();
        Accumulator mlbCareer = new Accumulator();
        Accumulator minorCareer = new Accumulator();
        Map<Integer, List<Map.Entry<Key, Accumulator>>> mlbByYear = new LinkedHashMap<>();

        for (Map.Entry<Key, Accumulator> e : splits.entrySet()) {
            if (!e.getValue().played()) {
                continue;
            }
            if (e.getKey().level() == LeagueLevel.MLB) {
                mlbByYear.computeIfAbsent(e.getKey().year(), y -> new ArrayList<>()).add(e);
            } else {
                Key k = e.getKey();
                minors.add(e.getValue().row(Kind.SPLIT, k.year(), k.level(), teams.get(k.teamId()), 1, false));
                minorCareer.add(e.getValue());
            }
        }
        for (Map.Entry<Integer, List<Map.Entry<Key, Accumulator>>> year : mlbByYear.entrySet()) {
            Accumulator yearTotal = new Accumulator();
            for (Map.Entry<Key, Accumulator> e : year.getValue()) {
                Key k = e.getKey();
                boolean champion = k.teamId().equals(championTeamByYear.get(k.year()));
                mlb.add(e.getValue().row(Kind.SPLIT, k.year(), LeagueLevel.MLB, teams.get(k.teamId()), 1, champion));
                yearTotal.add(e.getValue());
            }
            if (year.getValue().size() > 1) {
                mlb.add(yearTotal.row(Kind.YEAR_TOTAL, year.getKey(), LeagueLevel.MLB, null, year.getValue().size(), false));
            }
            mlbCareer.add(yearTotal);
        }
        if (!mlb.isEmpty()) {
            mlb.add(mlbCareer.row(Kind.CAREER_TOTAL, null, LeagueLevel.MLB, null, 0, false));
        }
        if (!minors.isEmpty()) {
            minors.add(minorCareer.row(Kind.CAREER_TOTAL, null, null, null, 0, false));
        }
        return new Tables(mlb, minors);
    }

    private static List<Row> withoutCareerTotal(List<Row> rows) {
        return rows.stream().filter(r -> r.kind() != Kind.CAREER_TOTAL).toList();
    }

    private record Key(int year, LeagueLevel level, Long teamId) {
    }

    private static final class Accumulator {
        final BattingLine batting = new BattingLine();
        final PitchingLine pitching = new PitchingLine();
        FieldingLine fielding;

        void add(BattingLine b, PitchingLine p, FieldingLine f) {
            if (b != null) batting.add(b);
            if (p != null) pitching.add(p);
            if (f != null) {
                if (fielding == null) fielding = new FieldingLine();
                fielding.add(f);
            }
        }

        void add(Accumulator other) {
            add(other.batting, other.pitching, other.fielding);
        }

        boolean played() {
            return batting.games > 0 || pitching.games > 0;
        }

        Row row(Kind kind, Integer year, LeagueLevel level, Team team, int teamCount, boolean champion) {
            return new Row(kind, year, level, team, teamCount, batting, pitching, fielding, champion);
        }
    }
}
