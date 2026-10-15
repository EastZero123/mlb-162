package com.mlbgame.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mlbgame.player.Hand;
import com.mlbgame.simulation.SimulationBalance.Baseline;
import com.mlbgame.simulation.SimulationBalance.Coefficients;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

public class PlateAppearanceModelTest {

    /** application.yml 의 game.simulation 과 같은 값 */
    public static SimulationBalance defaultBalance() {
        return new SimulationBalance(65,
                new Baseline(0.225, 0.085, 0.011, 0.031, 0.295, 0.23, 0.02, 0.45),
                new Coefficients(0.30, 0.20, 0.10, 0.25, 0.25, 0.35, 0.28, 0.07,
                        0.12, 0.04, 0.10, 0.06, 0.20, 0.20, 0.35, 0.15, 0.15, 0.10, 0.08));
    }

    final PlateAppearanceModel model = new PlateAppearanceModel(defaultBalance());
    final PitcherProfile mlbPitcher = PitcherProfile.average(65);

    private PaProbabilities vsMlb(BatterProfile batter) {
        return model.probabilities(batter, mlbPitcher, PaContext.NEUTRAL);
    }

    @Test
    void 메이저리그_평균끼리_만나면_MLB_리그_평균이_나온다() {
        PaProbabilities p = vsMlb(BatterProfile.average(65));

        assertThat(p.total()).isCloseTo(1.0, within(1e-9));
        assertThat(p.strikeout()).isCloseTo(0.225, within(1e-9));
        assertThat(p.walk()).isCloseTo(0.085, within(1e-9));
        assertThat(p.homeRun()).isCloseTo(0.031, within(1e-9));
        assertThat(p.expectedAverage()).isBetween(0.240, 0.255);   // MLB 평균 타율 .245~.250
        assertThat(p.expectedObp()).isBetween(0.305, 0.325);       // MLB 평균 출루율 ~.315
        assertThat(p.expectedSlg()).isBetween(0.380, 0.420);       // MLB 평균 장타율 ~.400
    }

    @Test
    void 확률_합계는_어떤_능력치에서도_1이다() {
        for (int rating = 1; rating <= 100; rating += 9) {
            PaProbabilities p = model.probabilities(BatterProfile.average(rating), PitcherProfile.average(101 - rating),
                    PaContext.NEUTRAL);
            assertThat(p.total()).as("rating " + rating).isCloseTo(1.0, within(1e-9));
            assertThat(p.asMap().values()).allSatisfy(v -> assertThat(v).isBetween(0.0, 1.0));
        }
    }

    @Test
    void OVR이_높을수록_타격_기록이_좋아진다() {
        PaProbabilities weak = vsMlb(BatterProfile.average(40));
        PaProbabilities average = vsMlb(BatterProfile.average(65));
        PaProbabilities star = vsMlb(BatterProfile.average(85));

        assertThat(weak.expectedAverage()).isLessThan(0.20);
        assertThat(star.expectedAverage()).isGreaterThan(0.29);          // 기준 이상 압축 후 약 .300 (시즌 시뮬레이션 .313)
        assertThat(star.homeRun()).isGreaterThan(0.04);          // 600타석 24홈런 이상 페이스 (압축 후 약 27홈런, 시즌 시뮬레이션 767타석 36홈런)
        assertThat(weak.strikeout()).isGreaterThan(average.strikeout());
        assertThat(star.strikeout()).isLessThan(average.strikeout());
    }

    @Test
    void 능력치는_각자_맡은_기록에만_영향을_준다() {
        BatterProfile base = BatterProfile.average(65);
        BatterProfile slugger = new BatterProfile(65, 65, 65, 90, 65, 65, 65, null);
        BatterProfile patient = new BatterProfile(65, 65, 90, 65, 65, 65, 65, null);
        BatterProfile contactHitter = new BatterProfile(65, 90, 65, 65, 65, 65, 65, null);

        assertThat(vsMlb(slugger).homeRun()).isGreaterThan(vsMlb(base).homeRun());
        assertThat(vsMlb(slugger).walk()).isCloseTo(vsMlb(base).walk(), within(1e-9));
        assertThat(vsMlb(patient).walk()).isGreaterThan(vsMlb(base).walk());
        assertThat(vsMlb(contactHitter).strikeout()).isLessThan(vsMlb(base).strikeout());
    }

    @Test
    void 투수_능력치가_높으면_타자_기록이_나빠진다() {
        BatterProfile batter = BatterProfile.average(65);
        PitcherProfile ace = new PitcherProfile(85, 85, 85, 85, 65, 65, 65, null);
        PitcherProfile wild = new PitcherProfile(65, 30, 65, 65, 65, 65, 65, null);
        PitcherProfile sinkerballer = new PitcherProfile(65, 65, 65, 65, 90, 65, 65, null);

        PaProbabilities vsAce = model.probabilities(batter, ace, PaContext.NEUTRAL);
        assertThat(vsAce.strikeout()).isGreaterThan(0.225);
        assertThat(vsAce.homeRun()).isLessThan(0.031);
        assertThat(model.probabilities(batter, wild, PaContext.NEUTRAL).walk()).isGreaterThan(0.085);

        PaProbabilities vsSinker = model.probabilities(batter, sinkerballer, PaContext.NEUTRAL);
        PaProbabilities vsAvg = model.probabilities(batter, mlbPitcher, PaContext.NEUTRAL);
        assertThat(vsSinker.groundOut() / (vsSinker.groundOut() + vsSinker.flyOut()))
                .isGreaterThan(vsAvg.groundOut() / (vsAvg.groundOut() + vsAvg.flyOut()));
    }

    @Test
    void 좌우_상성과_투수의_좌우_대응() {
        assertThat(PlateAppearanceModel.platoonShift(Hand.L, Hand.L, 65, 0.1)).isEqualTo(-0.05);
        assertThat(PlateAppearanceModel.platoonShift(Hand.R, Hand.L, 65, 0.1)).isEqualTo(0.1);
        assertThat(PlateAppearanceModel.platoonShift(Hand.S, Hand.L, 65, 0.1)).isPositive();   // 스위치는 항상 반대손
        assertThat(PlateAppearanceModel.platoonShift(Hand.R, Hand.L, 90, 0.1))
                .isLessThan(PlateAppearanceModel.platoonShift(Hand.R, Hand.L, 30, 0.1));        // 좌우 대응이 높으면 이점 감소
        assertThat(PlateAppearanceModel.platoonShift(null, Hand.L, 65, 0.1)).isZero();
    }

    @Test
    void 득점권에서는_클러치와_위기_관리가_작용한다() {
        BatterProfile clutchHitter = new BatterProfile(65, 65, 65, 65, 65, 65, 90, null);
        PaProbabilities normal = model.probabilities(clutchHitter, mlbPitcher, PaContext.NEUTRAL);
        PaProbabilities risp = model.probabilities(clutchHitter, mlbPitcher, PaContext.scoringPosition(true));

        assertThat(risp.expectedAverage()).isGreaterThan(normal.expectedAverage());
    }

    @Test
    void 같은_시드면_같은_결과가_나온다() {
        PaProbabilities p = vsMlb(BatterProfile.average(65));
        List<PaResult> first = new ArrayList<>();
        List<PaResult> second = new ArrayList<>();
        SplittableRandom a = new SplittableRandom(20261015L);
        SplittableRandom b = new SplittableRandom(20261015L);
        for (int i = 0; i < 200; i++) {
            first.add(p.draw(a));
            second.add(p.draw(b));
        }
        assertThat(first).isEqualTo(second);
    }

    @Test
    void 많이_뽑으면_확률대로_나온다() {
        PaProbabilities p = vsMlb(BatterProfile.average(65));
        SplittableRandom random = new SplittableRandom(7);
        int n = 200_000;
        int strikeouts = 0;
        int homeRuns = 0;
        for (int i = 0; i < n; i++) {
            PaResult r = p.draw(random);
            if (r == PaResult.STRIKEOUT) strikeouts++;
            if (r == PaResult.HOME_RUN) homeRuns++;
        }
        assertThat(strikeouts / (double) n).isCloseTo(0.225, within(0.005));
        assertThat(homeRuns / (double) n).isCloseTo(0.031, within(0.002));
    }
}
