package com.mlbgame.simulation.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.balance.SeasonHarness.Season;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 밸런스 검증: 시즌을 수백 번 반복해 OVR 별 기록 분포가 실제 MLB 와 비슷한지 확인한다.
 * 오래 걸리므로 일반 test 에서는 빠지고 {@code gradlew balanceTest} 로 따로 돌린다. 실행하면 리포트 표를 출력한다.
 *
 * <p>기준 (MLB 2023~2024 리그 평균 근사): 타율 .245~.250 / 출루율 .315 / 장타율 .400 / 600타석 약 19홈런 /
 * K% 22.5 / BB% 8.5 / ERA 4.0~4.3 / K/9 8.5~8.8 / BB/9 3.1~3.3 / HR/9 1.1~1.2
 */
@Tag("balance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BalanceVerificationTest {

    static final int SEASONS = 200;

    final SeasonHarness harness = new SeasonHarness();
    List<Season> mlb45;
    List<Season> mlb55;
    List<Season> mlb65;
    List<Season> mlb75;
    List<Season> mlb85;
    List<Season> sp65;
    List<Season> sp80;
    List<Season> cp65;

    @BeforeAll
    void simulateAll() {
        mlb45 = harness.seasons(SeasonHarness.player(Position.SS, 45), LeagueLevel.MLB, SEASONS, 1_000);
        mlb55 = harness.seasons(SeasonHarness.player(Position.SS, 55), LeagueLevel.MLB, SEASONS, 2_000);
        mlb65 = harness.seasons(SeasonHarness.player(Position.SS, 65), LeagueLevel.MLB, SEASONS, 3_000);
        mlb75 = harness.seasons(SeasonHarness.player(Position.SS, 75), LeagueLevel.MLB, SEASONS, 4_000);
        mlb85 = harness.seasons(SeasonHarness.player(Position.SS, 85), LeagueLevel.MLB, SEASONS, 5_000);
        sp65 = harness.seasons(SeasonHarness.player(Position.SP, 65), LeagueLevel.MLB, SEASONS, 6_000);
        sp80 = harness.seasons(SeasonHarness.player(Position.SP, 80), LeagueLevel.MLB, SEASONS, 7_000);
        cp65 = harness.seasons(SeasonHarness.player(Position.CP, 65), LeagueLevel.MLB, SEASONS, 8_000);

        System.out.println();
        System.out.println("===== 메이저리그 시즌 평균 (" + SEASONS + "시즌 반복, 능력치 전부 동일 가정) =====");
        SeasonHarness.printBattingHeader();
        SeasonHarness.printBatting("SS OVR 45", mlb45);
        SeasonHarness.printBatting("SS OVR 55", mlb55);
        SeasonHarness.printBatting("SS OVR 65", mlb65);
        SeasonHarness.printBatting("SS OVR 75", mlb75);
        SeasonHarness.printBatting("SS OVR 85", mlb85);
        SeasonHarness.printPitchingHeader();
        SeasonHarness.printPitching("SP OVR 65", sp65);
        SeasonHarness.printPitching("SP OVR 80", sp80);
        SeasonHarness.printPitching("CP OVR 65", cp65);
        System.out.println();
    }

    // ------------------------------------------------------------ 타자

    @Test
    void 메이저리그_평균_타자는_MLB_리그_평균_기록을_낸다() {
        BattingLine t = SeasonHarness.totalBatting(mlb65);
        double perSeason600 = 600.0 / (t.plateAppearances / (double) SEASONS);

        assertThat(t.getAverage()).isBetween(0.232, 0.262);
        assertThat(t.getObp()).isBetween(0.300, 0.335);
        assertThat(t.getSlg()).isBetween(0.370, 0.435);
        assertThat(t.homeRuns / (double) SEASONS * perSeason600).isBetween(14.0, 25.0);   // 600타석 환산 홈런
        assertThat((double) t.strikeouts / t.plateAppearances).isBetween(0.20, 0.25);
        assertThat((double) t.walks / t.plateAppearances).isBetween(0.07, 0.10);
    }

    @Test
    void OVR이_오를수록_OPS가_꾸준히_오른다() {
        double[] ops = {
                SeasonHarness.totalBatting(mlb45).getOps(),
                SeasonHarness.totalBatting(mlb55).getOps(),
                SeasonHarness.totalBatting(mlb65).getOps(),
                SeasonHarness.totalBatting(mlb75).getOps(),
                SeasonHarness.totalBatting(mlb85).getOps()};
        for (int i = 1; i < ops.length; i++) {
            assertThat(ops[i]).as("OVR 단계 " + i).isGreaterThan(ops[i - 1]);
        }
        // 최상위가 폭주하지 않게 위쪽도 막는다 (M5-3 커리어 검증에서 OVR 85 = OPS 1.16 이 나와 상한 추가)
        assertThat(ops[3]).as("OVR 75 = 좋은 주전").isBetween(0.770, 0.870);
        assertThat(ops[4]).as("OVR 85 = 올스타~MVP급").isBetween(0.860, 1.000);
        assertThat(ops[2]).as("OVR 65 = 리그 평균").isBetween(0.680, 0.770);
    }

    @Test
    void 메이저리그에서_OVR이_낮으면_출전이_줄어든다() {
        double paRegular = SeasonHarness.mean(mlb75, s -> s.batting().plateAppearances);   // 레벨 평균 +10 → 주전
        double paBench55 = SeasonHarness.mean(mlb55, s -> s.batting().plateAppearances);   // −10 → 벤치
        double paBench45 = SeasonHarness.mean(mlb45, s -> s.batting().plateAppearances);   // −20 → 벤치

        assertThat(paRegular).as("주전 시즌 타석").isBetween(560.0, 720.0);
        assertThat(paBench55).isLessThan(paRegular * 0.4);
        assertThat(paBench45).isLessThan(paRegular * 0.4);
    }

    @Test
    void 같은_OVR이어도_시즌마다_적당한_편차가_있다() {
        double sd = SeasonHarness.stdDev(mlb75, s -> s.batting().getAverage());
        assertThat(sd).as("주전 시즌 타율 표준편차").isBetween(0.010, 0.035);   // 실제 MLB 약 .015~.025
    }

    @Test
    void 레벨에_맞는_OVR이면_어느_레벨에서든_평균_기록이_나온다() {
        for (LeagueLevel level : List.of(LeagueLevel.ROOKIE, LeagueLevel.A, LeagueLevel.AA, LeagueLevel.AAA)) {
            List<Season> seasons = harness.seasons(
                    SeasonHarness.player(Position.OF, level.getAverageRating()), level, 60, 9_000 + level.ordinal());
            assertThat(SeasonHarness.totalBatting(seasons).getOps()).as(level.getLabel()).isBetween(0.660, 0.790);
        }
    }

    @Test
    void 수비가_좋으면_막은_실점이_플러스다() {
        FieldingLine good = SeasonHarness.totalFielding(mlb85);
        FieldingLine poor = SeasonHarness.totalFielding(mlb45);
        assertThat(good.getRunsSaved()).isPositive();
        assertThat(good.getRunsSaved() / SEASONS).isGreaterThan(poor.getRunsSaved() / SEASONS);
    }

    @Test
    void 부상_빈도는_시즌당_1회_미만이다() {
        assertThat(SeasonHarness.mean(mlb75, Season::injuries)).isBetween(0.15, 0.7);
        assertThat(SeasonHarness.mean(sp65, Season::injuries)).isBetween(0.15, 0.8);
    }

    // ------------------------------------------------------------ 투수

    @Test
    void 메이저리그_평균_선발투수는_MLB_평균_선발_기록을_낸다() {
        PitchingLine t = SeasonHarness.totalPitching(sp65);
        double starts = t.gamesStarted / (double) SEASONS;
        double innings = t.outs / 3.0 / SEASONS;

        assertThat(starts).as("시즌 선발 등판").isBetween(26.0, 36.0);
        assertThat(innings).as("시즌 이닝").isBetween(130.0, 200.0);
        assertThat(t.getEra()).isBetween(3.6, 5.0);
        assertThat(t.getStrikeoutsPerNine()).isBetween(7.5, 9.8);
        assertThat(t.getWalksPerNine()).isBetween(2.5, 3.9);
        assertThat(t.getHomeRunsPerNine()).isBetween(0.8, 1.5);
    }

    @Test
    void 에이스급_선발은_ERA가_낮고_이닝이_많다() {
        PitchingLine avg = SeasonHarness.totalPitching(sp65);
        PitchingLine ace = SeasonHarness.totalPitching(sp80);
        assertThat(ace.getEra()).isLessThan(avg.getEra() - 0.5);
        assertThat(ace.getEra()).as("에이스도 ERA 2점대 초반 아래로는 드물다").isGreaterThan(2.2);
        assertThat(ace.outs).isGreaterThan(avg.outs);
        assertThat(ace.wins).isGreaterThan(avg.wins);
    }

    @Test
    void 마무리는_시즌_50경기_안팎_등판하고_세이브를_쌓는다() {
        PitchingLine t = SeasonHarness.totalPitching(cp65);
        double games = t.games / (double) SEASONS;
        double saves = t.saves / (double) SEASONS;

        assertThat(games).as("시즌 등판").isBetween(40.0, 80.0);
        assertThat(saves).as("시즌 세이브").isBetween(15.0, 45.0);
        assertThat(t.outs / 3.0 / t.games).as("등판당 이닝").isLessThanOrEqualTo(1.0);
        assertThat(t.getEra()).isBetween(3.0, 5.2);
    }
}
