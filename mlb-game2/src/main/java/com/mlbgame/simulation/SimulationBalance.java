package com.mlbgame.simulation;

import com.mlbgame.league.LeagueMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 시뮬레이션 보정 수치. application.yml 의 game.simulation.* 로 조정한다.
 *
 * <p>모델: 사건별 확률 p 를 로그 오즈로 바꿔 능력치 차이만큼 더한 뒤 다시 확률로 되돌린다.
 * <pre>logit(p) = logit(기준 확률) + 계수 × (능력치 − 기준 능력치) / 10</pre>
 * 기준 능력치(메이저리그 평균 65)끼리 만나면 실제 MLB 리그 평균 기록이 나온다.
 *
 * <p>능력치 계수는 모든 모드 공통이고, 리그 평균 기록(baseline)만 모드별로 다르다 — 각 모드 1군 평균(OVR 65)끼리
 * 만나면 그 리그 평균 기록이 나온다 (docs/kbo-mode.md 3.7).
 *
 * @param baselineRating 리그 평균 기록에 대응하는 능력치 (최상위 레벨 평균 OVR)
 * @param baseline       메이저리그 평균 타석 결과 비율
 * @param coefficients   능력치 10 차이당 로그 오즈 변화량
 * @param kboBaseline    KBO 리그 평균 타석 결과 비율 (없으면 MLB 기준)
 */
@ConfigurationProperties("game.simulation")
public record SimulationBalance(
        int baselineRating,
        Baseline baseline,
        Coefficients coefficients,
        Baseline kboBaseline
) {

    @ConstructorBinding
    public SimulationBalance {
    }

    public SimulationBalance(int baselineRating, Baseline baseline, Coefficients coefficients) {
        this(baselineRating, baseline, coefficients, null);
    }

    /** 모드의 리그 평균 기록 */
    public Baseline baseline(LeagueMode mode) {
        return mode == LeagueMode.KBO && kboBaseline != null ? kboBaseline : baseline;
    }

    /**
     * 메이저리그 평균 (2023~2024 시즌 리그 평균 근사, 타석 기준)
     *
     * @param strikeoutRate 삼진 / 타석
     * @param walkRate      볼넷 / 타석
     * @param hitByPitchRate 몸에 맞는 공 / 타석
     * @param homeRunRate   홈런 / 타석
     * @param babip         인플레이 타구 중 안타 비율
     * @param doubleShare   인플레이 안타 중 2루타 비율
     * @param tripleShare   인플레이 안타 중 3루타 비율
     * @param groundBallShare 인플레이 아웃 중 땅볼 비율
     */
    public record Baseline(
            double strikeoutRate,
            double walkRate,
            double hitByPitchRate,
            double homeRunRate,
            double babip,
            double doubleShare,
            double tripleShare,
            double groundBallShare
    ) {
    }

    /**
     * 능력치 10 차이당 로그 오즈 변화량. 부호는 모델 쪽에서 붙인다 (예: 삼진 회피가 높으면 삼진↓).
     *
     * <p><b>균형 규칙:</b> 사건마다 타자 쪽 계수 합 = 투수 쪽 계수 합. 그래야 타자·투수 능력치가 같으면
     * 어느 레벨(루키 35 ~ 메이저 65)에서든 리그 평균 기록이 나온다. (3루타만 예외 — 비중이 작음)
     */
    public record Coefficients(
            double strikeoutAvoidK,
            double strikeoutPutAway,
            double strikeoutVelocity,
            double walkEye,
            double walkControl,
            double homeRunPower,
            double homeRunMovement,
            double homeRunVelocity,
            double babipContact,
            double babipSpeed,
            double babipVelocity,
            double babipMovement,
            double doubleGapPower,
            double doubleGroundBall,
            double tripleSpeed,
            double groundBallPitcher,
            double groundBallPower,
            /* 같은 손 대결 시 타자 불리함 (로그 오즈) — 투수의 좌우 대응이 높을수록 반대손 타자의 이점이 줄어든다 */
            double platoonEdge,
            /* 득점권에서 클러치 − 위기 관리 10 차이당 보정 */
            double clutch
    ) {
    }
}
