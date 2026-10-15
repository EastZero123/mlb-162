package com.mlbgame.league.world;

import com.mlbgame.league.Team;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * 커리어 시작 시점(2026시즌)의 리그 월드를 시드로 생성한다. DB 의존 없는 순수 로직 — 같은 시드면 같은 월드.
 *
 * <pre>
 * 구단 성향     재정 규모(실제 시장 규모 기반 고정) / 구단주 투자 성향 85~115% / 팜 점수 30~90
 * 팀 목표 전력  65 + 재정 보정(빅 +2, 스몰 −2) + (투자 성향 − 100)/10 + 정규분포 × 2.5
 * 선수 OVR      팀 목표 전력 + 역할 보정 + 정규분포 × 4  (35~92)
 * 능력치        OVR 주변으로 ±7 정도 흩뿌린 뒤 평균을 OVR 에 맞춤 (선수마다 개성)
 * </pre>
 */
public final class WorldGenerator {

    public static final int FIRST_SEASON = 2026;

    /** 주전 9명의 수비 위치 (마지막 한 자리는 지명타자 — 1루수/외야수로 기록) */
    static final List<Position> LINEUP = List.of(
            Position.C, Position.B1, Position.B2, Position.B3, Position.SS, Position.OF, Position.OF, Position.OF);
    static final List<Position> BENCH = List.of(Position.C, Position.SS, Position.B2, Position.OF);
    static final int[] ROTATION_OFFSETS = {6, 3, 1, -2, -4};
    static final double LINEUP_OFFSET = 1.5;
    static final double BENCH_OFFSET = -6;
    static final double BULLPEN_OFFSET = -3;
    static final double CLOSER_OFFSET = 2;

    private WorldGenerator() {
    }

    public record GeneratedNpc(
            String name,
            Position position,
            Hand bats,
            Hand throwsHand,
            int age,
            int potential,
            int[] attributes,
            RosterRole role,
            long salary,
            int contractYears,
            int serviceYears
    ) {
        public double overall() {
            double sum = 0;
            for (int value : attributes) sum += value;
            return sum / attributes.length;
        }
    }

    public record GeneratedTeam(
            Team team,
            Market market,
            int spendingPercent,
            int farmScore,
            long budget,
            List<GeneratedNpc> roster
    ) {
        public long payroll() {
            return roster.stream().mapToLong(GeneratedNpc::salary).sum();
        }
    }

    public static List<GeneratedTeam> generate(long seed, List<Team> teams) {
        RandomGenerator random = new SplittableRandom(seed);
        List<GeneratedTeam> result = new ArrayList<>();
        for (Team team : teams) {
            Market market = Market.of(team.getCode());
            int spending = 85 + random.nextInt(31);
            int farm = 30 + random.nextInt(61);
            long budget = Math.round(market.getBaseBudget() * spending / 100.0 / 1_000_000) * 1_000_000;
            double target = 65 + market.getTalentBonus() + (spending - 100) / 10.0 + random.nextGaussian() * 2.5;

            List<GeneratedNpc> roster = new ArrayList<>();
            for (Position position : LINEUP) {
                roster.add(npc(random, position, target + LINEUP_OFFSET, RosterRole.LINEUP));
            }
            Position dh = random.nextBoolean() ? Position.B1 : Position.OF;
            roster.add(npc(random, dh, target + LINEUP_OFFSET, RosterRole.LINEUP));
            for (Position position : BENCH) {
                roster.add(npc(random, position, target + BENCH_OFFSET, RosterRole.BENCH));
            }
            for (int offset : ROTATION_OFFSETS) {
                roster.add(npc(random, Position.SP, target + offset, RosterRole.ROTATION));
            }
            for (int i = 0; i < RosterRole.BULLPEN.getSlots(); i++) {
                roster.add(npc(random, Position.RP, target + BULLPEN_OFFSET, RosterRole.BULLPEN));
            }
            roster.add(npc(random, Position.CP, target + CLOSER_OFFSET, RosterRole.CLOSER));

            result.add(new GeneratedTeam(team, market, spending, farm, budget, List.copyOf(roster)));
        }
        return result;
    }

    /**
     * 스타 꼬리: 주전·선발·마무리의 {@value #STAR_CHANCE} 비율은 +6 ~ +25 더 강하다. 실제 MLB 재능 분포는 오른쪽으로 길어
     * 리그마다 MVP급(85~90)이 몇 명씩 있다. 정규분포만 쓰면 리그 최고가 80 안팎이라 내 선수가 매년 MVP 를 받았다 (M5-3).
     * 평균이 올라가지 않도록 기대 보너스({@link #STAR_MEAN})만큼 기준을 내린다.
     */
    static final double STAR_CHANCE = 0.12;
    static final double STAR_BASE = 6;
    static final double STAR_SPREAD = 7;
    static final double STAR_MEAN = STAR_CHANCE * (STAR_BASE + STAR_SPREAD * 0.798);   // E|N(0,1)| ≈ 0.798

    static double starBonus(RandomGenerator random, RosterRole role) {
        if (role == RosterRole.BENCH || role == RosterRole.BULLPEN) {
            return 0;
        }
        double bonus = random.nextDouble() < STAR_CHANCE ? STAR_BASE + Math.abs(random.nextGaussian()) * STAR_SPREAD : 0;
        return bonus - STAR_MEAN;
    }

    static GeneratedNpc npc(RandomGenerator random, Position position, double roleTarget, RosterRole role) {
        double star = starBonus(random, role);
        int overall = (int) Math.round(clamp(roleTarget + star + random.nextGaussian() * 4, 35, 92));
        int age = 22 + random.nextInt(9) + random.nextInt(8);          // 22~37, 29~30세가 가장 많음
        int potentialBonus = age <= 25 ? 3 + random.nextInt(13) : age <= 28 ? random.nextInt(6) : 0;
        int potential = Math.min(99, overall + potentialBonus);

        Hand throwsHand = throwsHand(random, position);
        Hand bats = position.isPitcher() ? throwsHand : batsHand(random);

        int serviceYears = Math.max(0, age - 23 - random.nextInt(3));
        long salary;
        int contractYears;
        if (serviceYears >= Payroll.FREE_AGENCY_SERVICE) {
            salary = Math.round(Payroll.marketValue(overall) * (0.8 + random.nextDouble() * 0.4) / 10_000) * 10_000;
            contractYears = 1 + random.nextInt(5);
        } else {
            salary = Payroll.salaryByService(serviceYears, overall);
            contractYears = 1;
        }

        return new GeneratedNpc(NameGenerator.next(random), position, bats, throwsHand, age, potential,
                attributes(random, overall), role, Math.max(Payroll.MINIMUM_SALARY, salary), contractYears, serviceYears);
    }

    /** 11개 능력치를 OVR 주변으로 흩뿌리고 평균을 OVR 에 맞춘다 */
    static int[] attributes(RandomGenerator random, int overall) {
        int count = Attribute.COUNT_PER_POSITION;
        double[] raw = new double[count];
        double sum = 0;
        for (int i = 0; i < count; i++) {
            raw[i] = overall + random.nextGaussian() * 7;
            sum += raw[i];
        }
        double shift = overall - sum / count;
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = (int) Math.round(clamp(raw[i] + shift, 20, 99));
        }
        return values;
    }

    private static Hand throwsHand(RandomGenerator random, Position position) {
        if (position.isPitcher()) {
            return random.nextInt(100) < 28 ? Hand.L : Hand.R;
        }
        if (position == Position.B1 || position == Position.OF) {
            return random.nextInt(100) < 20 ? Hand.L : Hand.R;
        }
        return Hand.R;   // 포수·2루·3루·유격은 우투
    }

    private static Hand batsHand(RandomGenerator random) {
        int roll = random.nextInt(100);
        return roll < 60 ? Hand.R : roll < 92 ? Hand.L : Hand.S;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
