package com.mlbgame.simulation.rules;

import com.mlbgame.simulation.game.UserHitter.HitterRole;
import com.mlbgame.simulation.game.UserPitcher.PitcherRole;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 부상 판정. 출전한 경기가 끝날 때마다 한 번 굴린다.
 *
 * <pre>
 * 기본 확률 (경기당)       타자 선발 0.25% / 대타 0.08% / 선발투수 1.2% / 마무리 0.4%
 * 내구성 보정              로그 오즈 −0.35 × (내구성 − 65)/10
 * 피로 70 초과             로그 오즈 +0.7 (약 2배)
 * 심각도                   경미 65% (1~3시리즈) / 중상 30% (5~20시리즈) / 시즌 아웃 5%
 * </pre>
 * 기본 확률은 MLB 선수가 시즌에 부상자 명단에 오르는 빈도(약 0.4~0.5회)에 맞췄다.
 */
public final class InjuryRules {

    static final double HITTER_START = 0.0025;
    static final double HITTER_PINCH = 0.0008;
    static final double STARTER = 0.012;
    static final double CLOSER = 0.004;
    static final double DURABILITY_COEF = 0.35;
    static final double TIRED_BOOST = 0.7;

    private InjuryRules() {
    }

    public record Injury(InjurySeverity severity, int seriesOut) {

        public boolean isSeasonEnding() {
            return severity == InjurySeverity.SEASON_ENDING;
        }
    }

    public static Optional<Injury> rollForHitter(HitterRole role, int durability, int fatigue, RandomGenerator random) {
        double base = switch (role) {
            case STARTER -> HITTER_START;
            case PINCH_HITTER -> HITTER_PINCH;
            case NONE -> 0;
        };
        return roll(base, durability, fatigue, random);
    }

    public static Optional<Injury> rollForPitcher(PitcherRole role, int durability, int fatigue, RandomGenerator random) {
        double base = switch (role) {
            case STARTER -> STARTER;
            case CLOSER, CLOSER_TUNE_UP -> CLOSER;
            case NONE -> 0;
        };
        return roll(base, durability, fatigue, random);
    }

    static double chance(double base, int durability, int fatigue) {
        if (base <= 0) {
            return 0;
        }
        double logit = Math.log(base / (1 - base)) - DURABILITY_COEF * (durability - 65) / 10.0;
        if (fatigue > Fatigue.TIRED_THRESHOLD) {
            logit += TIRED_BOOST;
        }
        return 1 / (1 + Math.exp(-logit));
    }

    private static Optional<Injury> roll(double base, int durability, int fatigue, RandomGenerator random) {
        // 출전 여부와 관계없이 항상 같은 횟수로 난수를 소비해 시드 재현이 흔들리지 않게 한다
        double injuryRoll = random.nextDouble();
        if (injuryRoll >= chance(base, durability, fatigue)) {
            return Optional.empty();
        }
        InjurySeverity severity = InjurySeverity.roll(random);
        return Optional.of(new Injury(severity, severity.rollSeriesOut(random)));
    }
}
