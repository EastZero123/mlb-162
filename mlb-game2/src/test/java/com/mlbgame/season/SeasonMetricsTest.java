package com.mlbgame.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.stats.AdvancedStats;
import com.mlbgame.league.stats.LeagueAverages;
import com.mlbgame.player.Position;
import com.mlbgame.season.SeasonMetrics.DetailRow;
import com.mlbgame.season.SeasonSplits.Kind;
import com.mlbgame.season.SeasonSplits.Row;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 세부 지표 표의 합계·통산 행: WAR 는 구간 합, 지수는 타석(이닝) 가중 평균, 마이너는 리그 평균 지표가 빈다 */
@Tag("bugfix")
class SeasonMetricsTest {

    private static BattingLine hitter(int pa, int hits) {
        BattingLine b = new BattingLine();
        b.games = pa / 4;
        b.plateAppearances = pa;
        b.atBats = pa;
        b.hits = hits;
        b.strikeouts = pa / 5;
        return b;
    }

    private static BattingLine sum(BattingLine... lines) {
        BattingLine total = new BattingLine();
        for (BattingLine line : lines) total.add(line);
        return total;
    }

    private static Row split(int year, LeagueLevel level, BattingLine b) {
        return new Row(Kind.SPLIT, year, level, null, 1, b, new PitchingLine(), null, false);
    }

    private static Row total(Kind kind, Integer year, LeagueLevel level, BattingLine b) {
        return new Row(kind, year, level, null, 2, b, new PitchingLine(), null, false);
    }

    @Test
    void 메이저_합계_행은_WAR를_더하고_wRC_플러스는_타석_가중_평균이다() {
        BattingLine a = hitter(200, 60);      // 2027 1팀
        BattingLine b = hitter(400, 100);     // 2027 2팀 (트레이드)
        BattingLine c = hitter(600, 170);     // 2028
        List<Row> rows = List.of(
                split(2027, LeagueLevel.MLB, a),
                split(2027, LeagueLevel.MLB, b),
                total(Kind.YEAR_TOTAL, 2027, LeagueLevel.MLB, sum(a, b)),
                split(2028, LeagueLevel.MLB, c),
                total(Kind.CAREER_TOTAL, null, LeagueLevel.MLB, sum(a, b, c)));

        List<DetailRow> detail = SeasonMetrics.rows(rows, Position.SS, false, year -> LeagueAverages.BASELINE);

        AdvancedStats.Hitter h1 = detail.get(0).hitter();
        AdvancedStats.Hitter h2 = detail.get(1).hitter();
        AdvancedStats.Hitter year = detail.get(2).hitter();
        AdvancedStats.Hitter h3 = detail.get(3).hitter();
        AdvancedStats.Hitter career = detail.get(4).hitter();

        assertThat(year.war()).isCloseTo(h1.war() + h2.war(), within(1e-9));
        assertThat(year.wrcPlus()).isCloseTo((h1.wrcPlus() * 200 + h2.wrcPlus() * 400) / 600, within(1e-9));
        assertThat(career.war()).isCloseTo(h1.war() + h2.war() + h3.war(), within(1e-9));
        // 리그 평균이 필요 없는 지표는 합친 기록으로 다시 계산
        assertThat(career.strikeoutRate()).isCloseTo(240.0 / 1200, within(1e-9));
    }

    @Test
    void 마이너_행은_리그_평균_지표가_비고_비율_지표는_있다() {
        BattingLine a = hitter(300, 90);
        List<Row> rows = List.of(
                split(2026, LeagueLevel.AA, a),
                total(Kind.CAREER_TOTAL, null, null, a));

        List<DetailRow> detail = SeasonMetrics.rows(rows, Position.SS, false, year -> null);

        assertThat(detail).allSatisfy(d -> {
            assertThat(d.hitter().wrcPlus()).isNull();
            assertThat(d.hitter().opsPlus()).isNull();
            assertThat(d.hitter().war()).isNull();
            assertThat(d.hitter().woba()).isPositive();
            assertThat(d.hitter().iso()).isZero();     // 단타만 쳤으니 순장타율 0
        });
    }

    @Test
    void 투수_통산_행은_WAR를_더하고_FIP는_이닝_가중_평균이다() {
        PitchingLine a = pitcher(300, 40);
        PitchingLine b = pitcher(150, 10);
        PitchingLine total = new PitchingLine();
        total.add(a);
        total.add(b);
        List<Row> rows = List.of(
                new Row(Kind.SPLIT, 2027, LeagueLevel.MLB, null, 1, new BattingLine(), a, null, false),
                new Row(Kind.SPLIT, 2028, LeagueLevel.MLB, null, 1, new BattingLine(), b, null, false),
                new Row(Kind.CAREER_TOTAL, null, LeagueLevel.MLB, null, 0, new BattingLine(), total, null, false));

        List<DetailRow> detail = SeasonMetrics.rows(rows, Position.SP, true, year -> LeagueAverages.BASELINE);

        AdvancedStats.Pitcher p1 = detail.get(0).pitcher();
        AdvancedStats.Pitcher p2 = detail.get(1).pitcher();
        AdvancedStats.Pitcher career = detail.get(2).pitcher();
        assertThat(career.war()).isCloseTo(p1.war() + p2.war(), within(1e-9));
        assertThat(career.fip()).isCloseTo((p1.fip() * 300 + p2.fip() * 150) / 450, within(1e-9));
    }

    private static PitchingLine pitcher(int outs, int strikeouts) {
        PitchingLine p = new PitchingLine();
        p.games = 10;
        p.gamesStarted = 10;
        p.outs = outs;
        p.battersFaced = outs + 40;
        p.runs = outs / 7;
        p.walks = 10;
        p.homeRuns = 3;
        p.strikeouts = strikeouts;
        return p;
    }
}
