package com.mlbgame.simulation.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.balance.SeasonHarness.Season;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * KBO 밸런스 검증 (K4): 1군 OVR 65 가 KBO 리그 평균 기록을 내는지, 144경기(3연전 36 + 2연전 18) 시즌 볼륨이 맞는지.
 * {@code gradlew balanceTest} 로 MLB 검증과 함께 돈다.
 *
 * <p>기준 (KBO 2023~2025 리그 평균 근사): 타율 .265~.270 / 출루율 .345 / 장타율 .405 / K% 19 / BB% 9 /
 * 600타석 약 14홈런 / ERA 4.3~4.7 / K/9 7.5 / BB/9 3.6 / HR/9 0.9
 */
@Tag("balance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KboBalanceVerificationTest {

    static final int SEASONS = 200;

    final SeasonHarness harness = new SeasonHarness();
    List<Season> kbo45;
    List<Season> kbo55;
    List<Season> kbo65;
    List<Season> kbo75;
    List<Season> kbo85;
    List<Season> futures55;
    List<Season> futuresHighSchool;
    List<Season> futuresCollege;
    List<Season> sp65;
    List<Season> sp80;
    List<Season> cp65;

    @BeforeAll
    void simulateAll() {
        kbo45 = harness.seasons(SeasonHarness.player(Position.SS, 45), LeagueLevel.KBO, SEASONS, 11_000);
        kbo55 = harness.seasons(SeasonHarness.player(Position.SS, 55), LeagueLevel.KBO, SEASONS, 12_000);
        kbo65 = harness.seasons(SeasonHarness.player(Position.SS, 65), LeagueLevel.KBO, SEASONS, 13_000);
        kbo75 = harness.seasons(SeasonHarness.player(Position.SS, 75), LeagueLevel.KBO, SEASONS, 14_000);
        kbo85 = harness.seasons(SeasonHarness.player(Position.SS, 85), LeagueLevel.KBO, SEASONS, 15_000);
        futures55 = harness.seasons(SeasonHarness.player(Position.OF, 55), LeagueLevel.FUTURES, SEASONS / 2, 16_000);
        futuresHighSchool = harness.seasons(SeasonHarness.player(Position.SS, 30), LeagueLevel.FUTURES, SEASONS / 2, 17_000);
        futuresCollege = harness.seasons(SeasonHarness.player(Position.SS, 40), LeagueLevel.FUTURES, SEASONS / 2, 18_000);
        sp65 = harness.seasons(SeasonHarness.player(Position.SP, 65), LeagueLevel.KBO, SEASONS, 19_000);
        sp80 = harness.seasons(SeasonHarness.player(Position.SP, 80), LeagueLevel.KBO, SEASONS, 20_000);
        cp65 = harness.seasons(SeasonHarness.player(Position.CP, 65), LeagueLevel.KBO, SEASONS, 21_000);

        System.out.println();
        System.out.println("===== KBO 1군 시즌 평균 (" + SEASONS + "시즌 반복, 144경기, 능력치 전부 동일 가정) =====");
        SeasonHarness.printBattingHeader();
        SeasonHarness.printBatting("SS OVR 45", kbo45);
        SeasonHarness.printBatting("SS OVR 55", kbo55);
        SeasonHarness.printBatting("SS OVR 65", kbo65);
        SeasonHarness.printBatting("SS OVR 75", kbo75);
        SeasonHarness.printBatting("SS OVR 85", kbo85);
        SeasonHarness.printBatting("퓨처스 OF 55", futures55);
        SeasonHarness.printBatting("퓨처스 고졸30", futuresHighSchool);
        SeasonHarness.printBatting("퓨처스 대졸40", futuresCollege);
        SeasonHarness.printPitchingHeader();
        SeasonHarness.printPitching("SP OVR 65", sp65);
        SeasonHarness.printPitching("SP OVR 80", sp80);
        SeasonHarness.printPitching("CP OVR 65", cp65);
        System.out.println();
    }

    // ------------------------------------------------------------ 타자

    @Test
    void 일군_평균_타자는_KBO_리그_평균_기록을_낸다() {
        BattingLine t = SeasonHarness.totalBatting(kbo65);
        double perSeason600 = 600.0 / (t.plateAppearances / (double) SEASONS);

        assertThat(t.getAverage()).isBetween(0.255, 0.282);
        assertThat(t.getObp()).isBetween(0.330, 0.362);
        assertThat(t.getSlg()).isBetween(0.380, 0.435);
        assertThat(t.homeRuns / (double) SEASONS * perSeason600).isBetween(10.0, 19.0);
        assertThat((double) t.strikeouts / t.plateAppearances).isBetween(0.16, 0.215);
        assertThat((double) t.walks / t.plateAppearances).isBetween(0.075, 0.105);
    }

    @Test
    void KBO_는_MLB_보다_타고투저다() {
        BattingLine kbo = SeasonHarness.totalBatting(kbo65);
        BattingLine mlb = SeasonHarness.totalBatting(
                harness.seasons(SeasonHarness.player(Position.SS, 65), LeagueLevel.MLB, 60, 22_000));
        assertThat(kbo.getAverage()).isGreaterThan(mlb.getAverage());
        assertThat((double) kbo.strikeouts / kbo.plateAppearances)
                .isLessThan((double) mlb.strikeouts / mlb.plateAppearances);
    }

    @Test
    void OVR이_오를수록_OPS가_꾸준히_오른다() {
        double[] ops = {
                SeasonHarness.totalBatting(kbo45).getOps(),
                SeasonHarness.totalBatting(kbo55).getOps(),
                SeasonHarness.totalBatting(kbo65).getOps(),
                SeasonHarness.totalBatting(kbo75).getOps(),
                SeasonHarness.totalBatting(kbo85).getOps()};
        for (int i = 1; i < ops.length; i++) {
            assertThat(ops[i]).as("OVR 단계 " + i).isGreaterThan(ops[i - 1]);
        }
        assertThat(ops[2]).as("OVR 65 = 리그 평균").isBetween(0.700, 0.800);
        assertThat(ops[3]).as("OVR 75 = 좋은 주전").isBetween(0.790, 0.910);
        assertThat(ops[4]).as("OVR 85 = MVP급").isBetween(0.880, 1.040);
    }

    @Test
    void 일군_주전은_144경기에_맞는_타석을_받는다() {
        double paRegular = SeasonHarness.mean(kbo75, s -> s.batting().plateAppearances);
        // KBO 는 출루율이 높아 경기당 타석이 MLB 보다 많다 (상위 타순 4.6~4.7). 실제 KBO 타석 1위가 650~690 수준이라
        // 단순히 MLB 범위 × 144/162 로 잡은 상한(650)은 너무 낮았다 (K4 1차 검증에서 OVR 75 = 671)
        assertThat(paRegular).as("주전 시즌 타석 (144경기, 상위 타순)").isBetween(520.0, 700.0);
        assertThat(SeasonHarness.mean(kbo55, s -> s.batting().plateAppearances)).isLessThan(paRegular * 0.4);
    }

    @Test
    void 퓨처스에서도_레벨에_맞는_OVR이면_평균_기록이_나온다() {
        assertThat(SeasonHarness.totalBatting(futures55).getOps()).isBetween(0.680, 0.820);
    }

    @Test
    void 퓨처스는_육성_리그라_신인도_출전한다() {
        // 고졸 OVR 30 = 플래툰(선발 약 40%), 대졸 OVR 40 = 주전 — K4 이전에는 둘 다 벤치(선발 15%)였다
        double highSchool = SeasonHarness.mean(futuresHighSchool, s -> s.batting().plateAppearances);
        double college = SeasonHarness.mean(futuresCollege, s -> s.batting().plateAppearances);
        assertThat(highSchool).as("고졸 신인 시즌 타석").isGreaterThan(150.0);
        assertThat(college).as("대졸 신인 시즌 타석").isGreaterThan(450.0);
    }

    // ------------------------------------------------------------ 투수

    @Test
    void 일군_평균_선발투수는_KBO_평균_선발_기록을_낸다() {
        PitchingLine t = SeasonHarness.totalPitching(sp65);
        double starts = t.gamesStarted / (double) SEASONS;
        double innings = t.outs / 3.0 / SEASONS;

        assertThat(starts).as("시즌 선발 등판 (144경기 / 5인 로테이션)").isBetween(23.0, 32.0);
        assertThat(innings).as("시즌 이닝").isBetween(115.0, 185.0);
        assertThat(t.getEra()).isBetween(3.9, 5.5);
        assertThat(t.getStrikeoutsPerNine()).isBetween(6.6, 8.8);
        assertThat(t.getWalksPerNine()).isBetween(2.9, 4.4);
        assertThat(t.getHomeRunsPerNine()).isBetween(0.6, 1.2);
    }

    @Test
    void 에이스는_ERA가_낮고_이닝이_많다() {
        PitchingLine avg = SeasonHarness.totalPitching(sp65);
        PitchingLine ace = SeasonHarness.totalPitching(sp80);
        assertThat(ace.getEra()).isLessThan(avg.getEra() - 0.5);
        assertThat(ace.getEra()).isGreaterThan(2.2);
        assertThat(ace.outs).isGreaterThan(avg.outs);
    }

    @Test
    void 마무리는_세이브를_쌓는다() {
        PitchingLine t = SeasonHarness.totalPitching(cp65);
        assertThat(t.games / (double) SEASONS).as("시즌 등판").isBetween(35.0, 72.0);
        assertThat(t.saves / (double) SEASONS).as("시즌 세이브").isBetween(12.0, 42.0);
        assertThat(t.getEra()).isBetween(3.2, 5.6);
    }
}
