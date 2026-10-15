package com.mlbgame.simulation;

import com.mlbgame.player.Hand;
import com.mlbgame.simulation.SimulationBalance.Baseline;
import com.mlbgame.simulation.SimulationBalance.Coefficients;
import org.springframework.stereotype.Component;

/**
 * 타석 결과 확률 모델 (로그 오즈 가산 방식).
 *
 * <ol>
 *   <li>삼진·볼넷·몸에 맞는 공·홈런 비율을 타석 기준으로 계산</li>
 *   <li>나머지 = 인플레이 타구. BABIP 로 안타/아웃을 나누고</li>
 *   <li>안타는 2루타·3루타 비율로, 아웃은 땅볼 비율로 다시 나눈다</li>
 * </ol>
 * 기준 능력치(65)끼리 만나면 {@link Baseline} 의 MLB 평균이 그대로 나온다. 설계: docs/simulation.md
 */
@Component
public class PlateAppearanceModel {

    /** 삼진+볼넷+사구+홈런이 타석의 이 비율을 넘지 않도록 제한 (극단값 방지) */
    static final double MAX_NON_BIP = 0.85;

    private final SimulationBalance balance;

    public PlateAppearanceModel(SimulationBalance balance) {
        this.balance = balance;
    }

    public PaProbabilities probabilities(BatterProfile batter, PitcherProfile pitcher, PaContext context) {
        Baseline base = balance.baseline();
        Coefficients c = balance.coefficients();
        double bf = context.batterFormOffset();
        double pf = context.pitcherFormOffset();

        // 타자에게 유리한 상황 보정 (좌우 상성 + 득점권 클러치)
        double edge = platoonShift(batter.bats(), pitcher.throwsHand(), pitcher.platoon(), c.platoonEdge());
        if (context.runnersInScoringPosition()) {
            edge += c.clutch() * (delta(batter.clutch(), bf) - delta(pitcher.composure(), pf));
        }

        double strikeout = shift(base.strikeoutRate(),
                -c.strikeoutAvoidK() * delta(batter.avoidK(), bf)
                        + c.strikeoutPutAway() * delta(pitcher.putAway(), pf)
                        + c.strikeoutVelocity() * delta(pitcher.velocity(), pf)
                        - edge);
        double walk = shift(base.walkRate(),
                c.walkEye() * delta(batter.eye(), bf)
                        - c.walkControl() * delta(pitcher.control(), pf)
                        + edge * 0.5);
        double hitByPitch = base.hitByPitchRate();
        double homeRun = shift(base.homeRunRate(),
                c.homeRunPower() * delta(batter.power(), bf)
                        - c.homeRunMovement() * delta(pitcher.movement(), pf)
                        - c.homeRunVelocity() * delta(pitcher.velocity(), pf)
                        + edge);

        double nonBip = strikeout + walk + hitByPitch + homeRun;
        if (nonBip > MAX_NON_BIP) {
            double scale = MAX_NON_BIP / nonBip;
            strikeout *= scale;
            walk *= scale;
            hitByPitch *= scale;
            homeRun *= scale;
            nonBip = MAX_NON_BIP;
        }
        double ballsInPlay = 1 - nonBip;

        double babip = shift(base.babip(),
                c.babipContact() * delta(batter.contact(), bf)
                        + c.babipSpeed() * delta(batter.speed(), bf)
                        - c.babipVelocity() * delta(pitcher.velocity(), pf)
                        - c.babipMovement() * delta(pitcher.movement(), pf)
                        + edge);
        double doubleShare = shift(base.doubleShare(),
                c.doubleGapPower() * delta(batter.gapPower(), bf)
                        - c.doubleGroundBall() * delta(pitcher.groundball(), pf));
        double tripleShare = shift(base.tripleShare(), c.tripleSpeed() * delta(batter.speed(), bf));
        double groundBallShare = shift(base.groundBallShare(),
                c.groundBallPitcher() * delta(pitcher.groundball(), pf)
                        - c.groundBallPower() * delta(batter.power(), bf));

        double hitsInPlay = ballsInPlay * babip;
        double triple = hitsInPlay * tripleShare;
        double doubles = hitsInPlay * doubleShare;
        double single = Math.max(0, hitsInPlay - triple - doubles);
        double outsInPlay = ballsInPlay - hitsInPlay;
        double groundOut = outsInPlay * groundBallShare;
        double flyOut = outsInPlay - groundOut;

        return new PaProbabilities(strikeout, walk, hitByPitch, single, doubles, triple, homeRun, groundOut, flyOut);
    }

    /** 기준 능력치 대비 차이 (10 단위) */
    private double delta(int rating, double formOffset) {
        return (rating + formOffset - balance.baselineRating()) / 10.0;
    }

    /**
     * 좌우 상성. 같은 손 대결이면 타자 불리(−edge/2), 반대손이면 타자 유리(+edge)이고,
     * 반대손 이점은 투수의 좌우 대응이 65보다 높을수록 줄고 낮을수록 커진다 (50 차이당 ±100%).
     * 스위치 타자는 항상 반대손으로 친다.
     *
     * <p>MLB 투수의 약 72%가 우완이라, 우타자 기준 평균 보정이 거의 0 이 되도록 같은 손 페널티를 절반으로 잡았다
     * (0.72 × −edge/2 + 0.28 × +edge ≈ 0). 기준 비율은 이미 좌우 대결이 섞인 리그 평균이기 때문.
     */
    static double platoonShift(Hand bats, Hand throwsHand, int pitcherPlatoon, double edge) {
        if (bats == null || throwsHand == null) {
            return 0;
        }
        boolean sameHand = bats != Hand.S && bats == throwsHand;
        if (sameHand) {
            return -edge / 2;
        }
        double scale = Math.max(0, Math.min(2, 1 - (pitcherPlatoon - 65) / 50.0));
        return edge * scale;
    }

    /** 확률 p 의 로그 오즈에 amount 를 더한 확률 */
    static double shift(double p, double amount) {
        double logit = Math.log(p / (1 - p)) + amount;
        return 1 / (1 + Math.exp(-logit));
    }
}
