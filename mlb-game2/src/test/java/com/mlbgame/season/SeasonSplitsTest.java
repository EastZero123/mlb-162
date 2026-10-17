package com.mlbgame.season;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.season.SeasonSplits.Kind;
import com.mlbgame.season.SeasonSplits.Row;
import com.mlbgame.season.SeasonSplits.Tables;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 시즌 기록을 연도·레벨·구단별로 나누는 규칙.
 * 시리즈 1개 = 타자 3경기, 4안타, 수비 기회 5·막은 안타 1 로 만들어 합계를 쉽게 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("bugfix")
class SeasonSplitsTest {

    @Autowired
    SeasonSplits seasonSplits;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    ObjectMapper objectMapper;

    Team yankees;
    Team redSox;
    List<SeriesRecord> records;
    int seriesNo;

    @BeforeEach
    void setUp() {
        List<Team> teams = teamRepository.findAllByOrderByIdAsc();
        yankees = teams.get(0);
        redSox = teams.get(1);
        records = new ArrayList<>();
    }

    private void series(int year, LeagueLevel level, Team team, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            BattingLine b = new BattingLine();
            b.games = 3;
            b.plateAppearances = 12;
            b.atBats = 11;
            b.hits = 4;
            FieldingLine f = new FieldingLine();
            f.chances = 5;
            f.hitsSaved = 1;
            add(year, level, team, b, f);
        }
    }

    /** 부상으로 내내 결장한 시리즈 (출전 0) */
    private void sidelined(int year, LeagueLevel level, Team team) throws Exception {
        add(year, level, team, new BattingLine(), new FieldingLine());
    }

    private void add(int year, LeagueLevel level, Team team, BattingLine b, FieldingLine f) throws Exception {
        String summary = objectMapper.writeValueAsString(new SeriesSummary(2, 1, b, f, null, null, 0, null));
        records.add(new SeriesRecord(1L, year, ++seriesNo, 0L, level, team.getId(), "{}", 2, 1, summary));
    }

    @Test
    void 같은_해_같은_레벨_같은_구단은_오가도_한_줄로_합친다() throws Exception {
        series(2027, LeagueLevel.AAA, yankees, 2);
        series(2027, LeagueLevel.MLB, yankees, 2);
        series(2027, LeagueLevel.AAA, yankees, 1);      // AAA → MLB → AAA

        Tables tables = seasonSplits.build(records, Map.of());

        assertThat(tables.minors()).extracting(Row::kind).containsExactly(Kind.SPLIT, Kind.CAREER_TOTAL);
        Row aaa = tables.minors().get(0);
        assertThat(aaa.level()).isEqualTo(LeagueLevel.AAA);
        assertThat(aaa.batting().games).isEqualTo(9);
        assertThat(aaa.batting().hits).isEqualTo(12);

        assertThat(tables.mlb()).extracting(Row::kind).containsExactly(Kind.SPLIT, Kind.CAREER_TOTAL);
        assertThat(tables.mlb().get(0).batting().games).isEqualTo(6);
    }

    @Test
    void 메이저에서_한_해에_두_팀을_뛰면_팀별_행과_합계_행이_생긴다() throws Exception {
        series(2028, LeagueLevel.MLB, yankees, 2);
        series(2028, LeagueLevel.MLB, redSox, 3);      // 트레이드
        series(2029, LeagueLevel.MLB, redSox, 1);

        Tables tables = seasonSplits.build(records, Map.of());

        assertThat(tables.mlb()).extracting(Row::kind)
                .containsExactly(Kind.SPLIT, Kind.SPLIT, Kind.YEAR_TOTAL, Kind.SPLIT, Kind.CAREER_TOTAL);
        assertThat(tables.mlb().get(0).team().getId()).isEqualTo(yankees.getId());
        assertThat(tables.mlb().get(1).team().getId()).isEqualTo(redSox.getId());
        Row total2028 = tables.mlb().get(2);
        assertThat(total2028.year()).isEqualTo(2028);
        assertThat(total2028.teamCount()).isEqualTo(2);
        assertThat(total2028.batting().games).isEqualTo(15);
        assertThat(tables.mlb().get(4).batting().games).isEqualTo(18);   // 통산
        assertThat(tables.minors()).isEmpty();
    }

    @Test
    void 마이너는_한_해에_여러_팀이어도_합계_행을_만들지_않는다() throws Exception {
        series(2026, LeagueLevel.AA, yankees, 1);
        series(2026, LeagueLevel.AA, redSox, 1);

        Tables tables = seasonSplits.build(records, Map.of());

        assertThat(tables.minors()).extracting(Row::kind).containsExactly(Kind.SPLIT, Kind.SPLIT, Kind.CAREER_TOTAL);
    }

    @Test
    void 내내_부상으로_결장한_구간은_줄을_만들지_않는다() throws Exception {
        sidelined(2026, LeagueLevel.AA, yankees);
        series(2026, LeagueLevel.AAA, yankees, 1);

        Tables tables = seasonSplits.build(records, Map.of());

        assertThat(tables.minors()).extracting(Row::level).containsExactly(LeagueLevel.AAA, null);
    }

    @Test
    void 수비_기록도_구간별로_쌓이고_통산이_합쳐진다() throws Exception {
        series(2027, LeagueLevel.AAA, yankees, 2);
        series(2028, LeagueLevel.AAA, yankees, 3);

        Tables tables = seasonSplits.build(records, Map.of());

        assertThat(tables.minors().get(0).fielding().chances).isEqualTo(10);
        assertThat(tables.minors().get(1).fielding().chances).isEqualTo(15);
        FieldingLine career = tables.minors().get(2).fielding();
        assertThat(career.chances).isEqualTo(25);
        assertThat(career.hitsSaved).isEqualTo(5);
    }

    @Test
    void 투수는_수비_기록이_비어_있다() throws Exception {
        PitchingLine p = new PitchingLine();
        p.games = 1;
        p.outs = 18;
        String summary = objectMapper.writeValueAsString(new SeriesSummary(2, 1, null, null, p, null, 0, null));
        records.add(new SeriesRecord(1L, 2027, 1, 0L, LeagueLevel.MLB, yankees.getId(), "{}", 2, 1, summary));

        Tables tables = seasonSplits.build(records, Map.of());

        assertThat(tables.mlb().get(0).pitching().games).isEqualTo(1);
        assertThat(tables.mlb()).allSatisfy(r -> assertThat(r.fielding()).isNull());
    }

    @Test
    void 우승한_해에_시즌을_마친_팀_행에만_우승_표시가_붙는다() throws Exception {
        series(2028, LeagueLevel.MLB, yankees, 2);
        series(2028, LeagueLevel.MLB, redSox, 2);

        Tables tables = seasonSplits.build(records, Map.of(2028, redSox.getId()));

        assertThat(tables.mlb().get(0).champion()).isFalse();     // 트레이드 전 팀
        assertThat(tables.mlb().get(1).champion()).isTrue();
        assertThat(tables.mlb().get(2).champion()).isFalse();     // 합계 행
    }
}
