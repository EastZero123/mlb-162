package com.mlbgame.league.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mlbgame.player.Position;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 세부 지표 계산식. 기준 리그 평균(BASELINE)과 똑같은 기록을 낸 선수는 리그 평균(100)이 나와야 한다 */
@Tag("bugfix")
class AdvancedStatsTest {

    static final LeagueAverages LEAGUE = LeagueAverages.BASELINE;

    /** 기준 리그 평균과 똑같은 타격 기록 (1,000타석) */
    private static BattingLine averageHitter(int games) {
        BattingLine b = new BattingLine();
        b.games = games;
        b.plateAppearances = 1000;
        b.atBats = 904;
        b.hits = 222;
        b.doubles = 44;
        b.triples = 4;
        b.homeRuns = 31;
        b.walks = 85;
        b.hitByPitch = 11;
        b.strikeouts = 225;
        return b;
    }

    /** 기준 리그 평균과 똑같은 투구 기록 (226.2이닝) */
    private static PitchingLine averagePitcher(boolean starter) {
        PitchingLine p = new PitchingLine();
        p.games = 30;
        p.gamesStarted = starter ? 30 : 0;
        p.outs = 680;
        p.battersFaced = 1000;
        p.runs = 118;
        p.homeRuns = 31;
        p.walks = 85;
        p.strikeouts = 225;
        return p;
    }

    @Test
    void 기준_리그_평균은_현실적인_값이다() {
        assertThat(LEAGUE.obp()).isCloseTo(0.318, within(0.001));
        assertThat(LEAGUE.slg()).isCloseTo(0.406, within(0.001));
        assertThat(LEAGUE.woba()).isCloseTo(0.317, within(0.001));
        assertThat(LEAGUE.era()).isCloseTo(4.69, within(0.01));
        assertThat(LEAGUE.usable()).isTrue();
    }

    @Test
    void 리그_평균_타자는_wRC_플러스와_OPS_플러스가_100이다() {
        AdvancedStats.Hitter h = AdvancedStats.hitter(averageHitter(0), null, Position.SS, LEAGUE);

        assertThat(h.woba()).isCloseTo(LEAGUE.woba(), within(1e-9));
        assertThat(h.wrcPlus()).isCloseTo(100, within(1e-6));
        assertThat(h.opsPlus()).isCloseTo(100, within(1e-6));
        // 타격·주루·수비·포지션 0 → 대체 선수 보정만: 20 × 1000/600 / 10
        assertThat(h.war()).isCloseTo(20.0 * 1000 / 600 / 10, within(1e-6));
    }

    @Test
    void 리그_평균이_필요_없는_타자_지표() {
        AdvancedStats.Hitter h = AdvancedStats.hitter(averageHitter(0), null, Position.SS, LEAGUE);

        assertThat(h.iso()).isCloseTo(0.406 - 0.2456, within(0.001));
        assertThat(h.walkRate()).isCloseTo(0.085, within(1e-9));
        assertThat(h.strikeoutRate()).isCloseTo(0.225, within(1e-9));
        // BABIP = (H − HR) / (AB − K − HR + SF) = 191 / 648
        assertThat(h.babip()).isCloseTo(191.0 / 648, within(1e-9));
    }

    @Test
    void 포지션_보정은_162경기에_유격수와_1루수가_2승_차이() {
        double shortstop = AdvancedStats.hitter(averageHitter(162), null, Position.SS, LEAGUE).war();
        double firstBase = AdvancedStats.hitter(averageHitter(162), null, Position.B1, LEAGUE).war();
        assertThat(shortstop - firstBase).isCloseTo((7.5 + 12.5) / 10, within(1e-6));
    }

    @Test
    void 주루와_수비가_WAR에_더해진다() {
        BattingLine b = averageHitter(0);
        double base = AdvancedStats.hitter(b, null, Position.OF, LEAGUE).war();

        b.stolenBases = 10;           // +2.0점
        b.caughtStealing = 5;         // −2.0점
        FieldingLine f = new FieldingLine();
        f.hitsSaved = 4;              // +3.0점 → 막은 실점 3.0
        double withExtras = AdvancedStats.hitter(b, f, Position.OF, LEAGUE).war();

        assertThat(withExtras - base).isCloseTo(3.0 / 10, within(1e-6));
    }

    @Test
    void 리그_평균이_없으면_wRC_플러스_OPS_플러스_WAR는_비고_나머지는_계산한다() {
        AdvancedStats.Hitter h = AdvancedStats.hitter(averageHitter(10), null, Position.SS, null);

        assertThat(h.wrcPlus()).isNull();
        assertThat(h.opsPlus()).isNull();
        assertThat(h.war()).isNull();
        assertThat(h.woba()).isPositive();
        assertThat(h.iso()).isPositive();
    }

    @Test
    void 리그_평균_투수는_FIP가_리그_ERA이고_ERA_플러스와_FIP_마이너스가_100이다() {
        AdvancedStats.Pitcher p = AdvancedStats.pitcher(averagePitcher(true), LEAGUE);

        assertThat(p.fip()).isCloseTo(LEAGUE.era(), within(1e-9));
        assertThat(p.eraPlus()).isCloseTo(100, within(1e-6));
        assertThat(p.fipMinus()).isCloseTo(100, within(1e-6));
        assertThat(p.strikeoutRate()).isCloseTo(0.225, within(1e-9));
        assertThat(p.kMinusBb()).isCloseTo(0.14, within(1e-9));
    }

    @Test
    void 투수_WAR_대체_선수_기준은_선발_1점_불펜_0점6() {
        double innings = 680 / 3.0;
        assertThat(AdvancedStats.pitcher(averagePitcher(true), LEAGUE).war())
                .isCloseTo(1.0 * innings / 9 / 10, within(1e-6));
        assertThat(AdvancedStats.pitcher(averagePitcher(false), LEAGUE).war())
                .isCloseTo(0.6 * innings / 9 / 10, within(1e-6));
    }

    @Test
    void 무실점_투수는_ERA_플러스를_표시하지_않는다() {
        PitchingLine p = averagePitcher(false);
        p.runs = 0;
        assertThat(AdvancedStats.pitcher(p, LEAGUE).eraPlus()).isNull();
    }

    @Test
    void 리그_평균이_없으면_투수는_비율_지표만_계산한다() {
        AdvancedStats.Pitcher p = AdvancedStats.pitcher(averagePitcher(true), null);
        assertThat(p.fip()).isNull();
        assertThat(p.eraPlus()).isNull();
        assertThat(p.war()).isNull();
        assertThat(p.walkRate()).isCloseTo(0.085, within(1e-9));
    }
}
