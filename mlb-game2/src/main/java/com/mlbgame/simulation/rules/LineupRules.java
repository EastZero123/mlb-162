package com.mlbgame.simulation.rules;

import com.mlbgame.common.Messages;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.UserHitter.HitterRole;
import java.util.random.RandomGenerator;

/**
 * 타자의 출전 판정과 자동 타순. 소속 레벨 평균 OVR 대비 내 선수 OVR 로 팀 내 입지가 정해진다.
 *
 * <pre>
 * OVR − 레벨 평균   입지      경기별 선발 확률
 * 0 이상           주전      100% (피로 80 이상이면 휴식일 → 대타 대기)
 * −6 ~ 0           플래툰    75% + 차이 × 6%  (−6 → 39%)
 * −6 미만          벤치      15%
 * 선발이 아니면 50% 확률로 대타 대기, 아니면 결장
 * </pre>
 */
public final class LineupRules {

    public static final int PLATOON_RANGE = 6;
    public static final int REST_FATIGUE = 80;
    static final double PLATOON_BASE = 0.75;
    static final double PLATOON_PER_POINT = 0.06;
    static final double BENCH_START = 0.15;
    static final double PINCH_HIT_CHANCE = 0.5;

    private LineupRules() {
    }

    public enum Standing {
        REGULAR("주전"),
        PLATOON("플래툰"),
        BENCH("벤치");

        private final String label;

        Standing(String label) {
            this.label = label;
        }

        public String getLabel() {
            return Messages.text("LineupRules.Standing." + name(), label);
        }
    }

    public static Standing standing(double overall, LeagueLevel level) {
        double diff = overall - level.getAverageRating();
        if (diff >= 0) {
            return Standing.REGULAR;
        }
        return diff >= -PLATOON_RANGE ? Standing.PLATOON : Standing.BENCH;
    }

    /** 오늘 경기의 역할 */
    public static HitterRole roleForGame(double overall, LeagueLevel level, int fatigue, RandomGenerator random) {
        double diff = overall - level.getAverageRating();
        double startChance = switch (standing(overall, level)) {
            case REGULAR -> fatigue >= REST_FATIGUE ? 0 : 1;
            case PLATOON -> PLATOON_BASE + diff * PLATOON_PER_POINT;
            case BENCH -> BENCH_START;
        };
        if (random.nextDouble() < startChance) {
            return HitterRole.STARTER;
        }
        return random.nextDouble() < PINCH_HIT_CHANCE ? HitterRole.PINCH_HITTER : HitterRole.NONE;
    }

    /**
     * 자동 타순 (1~9). 입지로 타순 구간을 정하고, 능력치 성향으로 그 안의 자리를 고른다.
     * <ul>
     *   <li>주력형(주력·도루·선구안) → 1번</li>
     *   <li>컨택형(컨택·삼진 회피) → 주전 상위는 3번, 그 외 2번</li>
     *   <li>파워형(파워·갭 파워) → 주전 상위는 4번, 그 외 5~6번</li>
     *   <li>주전 하위(차이 0~3)는 6번, 주력형은 9번 / 플래툰 7번 / 벤치 8번</li>
     *   <li>포수는 한 자리 아래 (9번은 그대로)</li>
     * </ul>
     */
    public static int battingSlot(PlayerSnapshot player, LeagueLevel level) {
        double diff = player.overall() - level.getAverageRating();
        double speed = avg(player, Attribute.SPEED, Attribute.STEALING, Attribute.EYE);
        double contact = avg(player, Attribute.CONTACT, Attribute.AVOID_K);
        double power = avg(player, Attribute.POWER, Attribute.GAP_POWER);
        Style style = speed >= contact && speed >= power ? Style.SPEED
                : (contact >= power ? Style.CONTACT : Style.POWER);

        int slot;
        if (diff >= 8) {
            slot = switch (style) {
                case SPEED -> 1;
                case CONTACT -> 3;
                case POWER -> 4;
            };
        } else if (diff >= 3) {
            slot = switch (style) {
                case SPEED -> 1;
                case CONTACT -> 2;
                case POWER -> 5;
            };
        } else if (diff >= 0) {
            slot = style == Style.SPEED ? 9 : 6;   // 주력형은 "두 번째 리드오프" 9번
        } else if (diff >= -PLATOON_RANGE) {
            slot = 7;
        } else {
            slot = 8;
        }
        if (player.position() == Position.C && slot < 9) {
            slot++;
        }
        return slot;
    }

    private enum Style {SPEED, CONTACT, POWER}

    private static double avg(PlayerSnapshot player, Attribute... attributes) {
        double sum = 0;
        for (Attribute attribute : attributes) {
            sum += player.attribute(attribute);
        }
        return sum / attributes.length;
    }
}
