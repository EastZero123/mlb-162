package com.mlbgame.league.world;

import com.mlbgame.player.Hand;
import com.mlbgame.player.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * 시즌 전환 시 리그 월드 변화 (같은 시드면 같은 결과). 설계: docs/league-world.md 4장
 *
 * <ol>
 *   <li>나이 +1, 성장·노화 (나이·잠재력 + 운: 급성장·급락 각 4%)</li>
 *   <li>은퇴 (40세 이상, 36세 이상 확률, 기량 하락 베테랑)</li>
 *   <li>계약 1년 경과 → 서비스 6년 미만은 재계약(최저·연봉조정), 6년 이상 만료자는 FA</li>
 *   <li>구단 예산(성적·사치세)·팜 점수(신인 드래프트: 하위 팀이 앞 순번) 갱신</li>
 *   <li>FA 시장: OVR 순으로, 예산 여유가 있고 전력이 보강되는 구단이 영입 (빅마켓은 더 비싸게)</li>
 *   <li>로스터 재편: 구단별 26명을 역할에 맞게 다시 배치, 모자라면 팜 점수로 유망주 콜업, 넘치면 마이너로</li>
 * </ol>
 */
public final class WorldEvolution {

    static final double BREAKOUT_RATE = 0.04;
    static final double COLLAPSE_RATE = 0.04;
    static final int MAX_AGE = 40;

    private WorldEvolution() {
    }

    public record Signing(String name, double overall, Long teamId, long salary, int years) {
    }

    /**
     * @param retired     이번에 은퇴한 NPC 수
     * @param freeAgents  FA 가 된 NPC 수
     * @param signings    FA 계약 (연봉 순)
     * @param callUps     새로 콜업된 유망주 수
     */
    public record Report(int retired, int freeAgents, List<Signing> signings, int callUps) {
    }

    /**
     * @param npcs    은퇴하지 않은 NPC 전원 (MLB·MINORS·FA) — 상태가 직접 바뀐다
     * @param winPct  구단 id → 지난 시즌 승률
     * @param created 새로 만든 NPC (콜업 유망주) — 호출한 쪽이 저장
     */
    public static Report evolve(long seed, Long worldId, List<WorldTeam> teams, List<NpcPlayer> npcs,
                                Map<Long, Double> winPct, List<NpcPlayer> created) {
        RandomGenerator rng = new SplittableRandom(seed);
        int retired = 0;
        int freeAgents = 0;

        // 1~3. 성장·노화, 은퇴, 계약
        for (NpcPlayer npc : npcs) {
            boolean wasUnsignedFreeAgent = npc.getStatus() == NpcStatus.FA;
            npc.ageOneYear();
            develop(npc, rng);
            if (shouldRetire(npc, wasUnsignedFreeAgent, rng)) {
                npc.retire();
                retired++;
                continue;
            }
            if (npc.getStatus() == NpcStatus.FA) {
                continue;
            }
            npc.passContractYear();
            if (npc.getContractYears() == 0) {
                if (npc.getServiceYears() < Payroll.FREE_AGENCY_SERVICE) {
                    npc.renew(Payroll.salaryByService(npc.getServiceYears(), npc.getOverall()));
                } else {
                    npc.becomeFreeAgent();
                    freeAgents++;
                }
            }
        }

        // 4. 예산·팜
        Map<Long, WorldTeam> teamById = teams.stream().collect(Collectors.toMap(t -> t.getTeam().getId(), t -> t));
        updateFinances(teams, npcs, winPct, rng);

        // 5. FA 시장
        List<Signing> signings = freeAgency(npcs, teamById, rng);

        // 6. 로스터 재편 + 콜업
        int callUps = 0;
        for (WorldTeam team : teams) {
            callUps += rebuildRoster(worldId, team, npcs, created, rng);
        }
        signings.sort(Comparator.comparingLong(Signing::salary).reversed());
        return new Report(retired, freeAgents, signings, callUps);
    }

    // ------------------------------------------------------------ 성장·노화

    static int growthBase(int age) {
        if (age <= 24) return 2;
        if (age <= 27) return 1;
        if (age <= 30) return 0;
        if (age <= 33) return -1;
        if (age <= 36) return -2;
        return -3;
    }

    static void develop(NpcPlayer npc, RandomGenerator rng) {
        int base = growthBase(npc.getAge());
        double roll = rng.nextDouble();
        int swing = 0;
        if (roll < BREAKOUT_RATE && npc.getAge() <= 27) swing = 3;
        else if (roll > 1 - COLLAPSE_RATE) swing = -3;
        boolean capped = base > 0 && npc.getOverall() >= npc.getPotential();

        int[] values = npc.attributeArray();
        for (int i = 0; i < values.length; i++) {
            int delta = (capped ? 0 : base) + swing + (int) Math.round(rng.nextGaussian() * 1.2);
            values[i] += delta;
        }
        npc.updateAttributes(values);
    }

    static boolean shouldRetire(NpcPlayer npc, boolean unsignedLastYear, RandomGenerator rng) {
        double ovr = npc.getOverall();
        if (npc.getAge() >= MAX_AGE) return true;
        if (unsignedLastYear && npc.getAge() >= 34) return true;
        if (npc.getAge() >= 36 && rng.nextDouble() < 0.25 + Math.max(0, 60 - ovr) / 40) return true;
        return npc.getAge() > 30 && ovr < 48 && rng.nextDouble() < 0.5;
    }

    // ------------------------------------------------------------ 재정

    static void updateFinances(List<WorldTeam> teams, List<NpcPlayer> npcs, Map<Long, Double> winPct, RandomGenerator rng) {
        Map<Long, Long> payroll = payrolls(npcs);
        List<WorldTeam> draftOrder = new ArrayList<>(teams);
        draftOrder.sort(Comparator.comparingDouble(t -> winPct.getOrDefault(t.getTeam().getId(), 0.5)));
        for (int rank = 0; rank < draftOrder.size(); rank++) {
            WorldTeam team = draftOrder.get(rank);
            double pct = winPct.getOrDefault(team.getTeam().getId(), 0.5);
            double budget = team.getMarket().getBaseBudget() * team.getSpendingPercent() / 100.0 * (1 + (pct - 0.5) * 0.4);
            if (payroll.getOrDefault(team.getTeam().getId(), 0L) > Payroll.LUXURY_TAX_THRESHOLD) {
                budget *= 0.95;                                      // 사치세 부담
            }
            double draftBonus = 6.0 * (draftOrder.size() - 1 - rank) / (draftOrder.size() - 1);  // 꼴찌 +6
            int farm = (int) Math.round(team.getFarmScore() * 0.85 + 9 + draftBonus + rng.nextGaussian() * 3);
            team.updateFinances(Math.round(budget / 1_000_000) * 1_000_000, Math.max(20, Math.min(95, farm)));
        }
    }

    static Map<Long, Long> payrolls(List<NpcPlayer> npcs) {
        Map<Long, Long> payroll = new HashMap<>();
        for (NpcPlayer npc : npcs) {
            if (npc.getStatus() == NpcStatus.MLB && npc.getTeamId() != null) {
                payroll.merge(npc.getTeamId(), npc.getSalary(), Long::sum);
            }
        }
        return payroll;
    }

    // ------------------------------------------------------------ FA

    static List<Signing> freeAgency(List<NpcPlayer> npcs, Map<Long, WorldTeam> teams, RandomGenerator rng) {
        Map<Long, Long> payroll = payrolls(npcs);
        List<NpcPlayer> pool = npcs.stream()
                .filter(n -> n.getStatus() == NpcStatus.FA)
                .sorted(Comparator.comparingDouble(NpcPlayer::getOverall).reversed())
                .toList();
        List<Signing> signings = new ArrayList<>();
        for (NpcPlayer fa : pool) {
            WorldTeam best = null;
            double bestScore = 0;
            long bestSalary = 0;
            for (WorldTeam team : teams.values()) {
                long salary = askingSalary(fa, team.getMarket(), rng);
                long room = team.getBudget() - payroll.getOrDefault(team.getTeam().getId(), 0L);
                if (room < salary || !improves(team.getTeam().getId(), fa, npcs)) {
                    continue;
                }
                double score = room * (team.getMarket() == Market.BIG ? 1.3 : 1.0) * (0.7 + 0.6 * rng.nextDouble());
                if (score > bestScore) {
                    bestScore = score;
                    best = team;
                    bestSalary = salary;
                }
            }
            if (best != null) {
                int years = fa.getAge() <= 29 ? 3 + rng.nextInt(3) : fa.getAge() <= 33 ? 2 + rng.nextInt(2) : 1;
                fa.sign(best.getTeam().getId(), bestSalary, years);
                payroll.merge(best.getTeam().getId(), bestSalary, Long::sum);
                signings.add(new Signing(fa.getName(), fa.getOverall(), best.getTeam().getId(), bestSalary, years));
            }
        }
        return signings;
    }

    static long askingSalary(NpcPlayer fa, Market market, RandomGenerator rng) {
        double factor = switch (market) {
            case BIG -> 1.1;
            case MID -> 1.0;
            case SMALL -> 0.9;
        };
        double salary = Payroll.marketValue(fa.getOverall()) * (0.9 + 0.3 * rng.nextDouble()) * factor;
        return Math.max(Payroll.MINIMUM_SALARY, Math.round(salary / 10_000) * 10_000);
    }

    /** 같은 역할군에서 가장 약한 1군 선수보다 나으면 보강 */
    static boolean improves(Long teamId, NpcPlayer fa, List<NpcPlayer> npcs) {
        boolean pitcher = fa.getPosition().isPitcher();
        List<NpcPlayer> group = npcs.stream()
                .filter(n -> teamId.equals(n.getTeamId()) && n.getStatus() == NpcStatus.MLB
                        && n.getPosition().isPitcher() == pitcher)
                .toList();
        if (group.size() < RosterRole.ROSTER_SIZE / 2) {           // 타자·투수 각 13명이 안 되면 무조건 보강
            return true;
        }
        double weakest = group.stream().mapToDouble(NpcPlayer::getOverall).min().orElse(0);
        return fa.getOverall() > weakest + 2;
    }

    // ------------------------------------------------------------ 로스터 재편

    /** 구단 26명 재배치. 모자란 자리는 유망주 콜업. 반환: 콜업 수 */
    static int rebuildRoster(Long worldId, WorldTeam team, List<NpcPlayer> npcs, List<NpcPlayer> created,
                             RandomGenerator rng) {
        Long teamId = team.getTeam().getId();
        List<NpcPlayer> mine = npcs.stream()
                .filter(n -> teamId.equals(n.getTeamId())
                        && (n.getStatus() == NpcStatus.MLB || n.getStatus() == NpcStatus.MINORS))
                .collect(Collectors.toCollection(ArrayList::new));
        int callUps = 0;

        // 타자: 포수·유격수를 먼저 확보하고 OVR 순으로 주전 9 + 벤치 4
        List<NpcPlayer> hitters = sortedByOverall(mine.stream().filter(n -> !n.getPosition().isPitcher()).toList());
        List<NpcPlayer> lineup = new ArrayList<>();
        for (Position required : List.of(Position.C, Position.SS)) {
            NpcPlayer best = hitters.stream().filter(n -> n.getPosition() == required && !lineup.contains(n)).findFirst()
                    .orElse(null);
            if (best == null) {
                best = callUp(worldId, team, required, RosterRole.LINEUP, rng);
                created.add(best);
                callUps++;
            }
            lineup.add(best);
        }
        for (NpcPlayer h : hitters) {
            if (lineup.size() >= RosterRole.LINEUP.getSlots()) break;
            if (!lineup.contains(h)) lineup.add(h);
        }
        while (lineup.size() < RosterRole.LINEUP.getSlots()) {
            NpcPlayer p = callUp(worldId, team, Position.OF, RosterRole.LINEUP, rng);
            created.add(p);
            lineup.add(p);
            callUps++;
        }
        List<NpcPlayer> bench = new ArrayList<>(hitters.stream().filter(h -> !lineup.contains(h))
                .limit(RosterRole.BENCH.getSlots()).toList());
        while (bench.size() < RosterRole.BENCH.getSlots()) {
            NpcPlayer p = callUp(worldId, team, bench.isEmpty() ? Position.C : Position.B2, RosterRole.BENCH, rng);
            created.add(p);
            bench.add(p);
            callUps++;
        }

        // 투수: 선발 5 (선발 포지션), 마무리 1 (마무리 우선), 불펜 7
        List<NpcPlayer> pitchers = sortedByOverall(mine.stream().filter(n -> n.getPosition().isPitcher()).toList());
        List<NpcPlayer> rotation = new ArrayList<>(pitchers.stream().filter(n -> n.getPosition() == Position.SP)
                .limit(RosterRole.ROTATION.getSlots()).toList());
        while (rotation.size() < RosterRole.ROTATION.getSlots()) {
            NpcPlayer p = callUp(worldId, team, Position.SP, RosterRole.ROTATION, rng);
            created.add(p);
            rotation.add(p);
            callUps++;
        }
        List<NpcPlayer> relievers = pitchers.stream().filter(n -> !rotation.contains(n)).collect(Collectors.toCollection(ArrayList::new));
        NpcPlayer closer = relievers.stream().filter(n -> n.getPosition() == Position.CP).findFirst()
                .orElse(relievers.isEmpty() ? null : relievers.get(0));
        if (closer == null) {
            closer = callUp(worldId, team, Position.CP, RosterRole.CLOSER, rng);
            created.add(closer);
            callUps++;
        }
        relievers.remove(closer);
        List<NpcPlayer> bullpen = new ArrayList<>(relievers.stream().limit(RosterRole.BULLPEN.getSlots()).toList());
        while (bullpen.size() < RosterRole.BULLPEN.getSlots()) {
            NpcPlayer p = callUp(worldId, team, Position.RP, RosterRole.BULLPEN, rng);
            created.add(p);
            bullpen.add(p);
            callUps++;
        }

        lineup.forEach(n -> n.assign(teamId, RosterRole.LINEUP));
        bench.forEach(n -> n.assign(teamId, RosterRole.BENCH));
        rotation.forEach(n -> n.assign(teamId, RosterRole.ROTATION));
        closer.assign(teamId, RosterRole.CLOSER);
        bullpen.forEach(n -> n.assign(teamId, RosterRole.BULLPEN));

        List<NpcPlayer> roster = new ArrayList<>();
        roster.addAll(lineup);
        roster.addAll(bench);
        roster.addAll(rotation);
        roster.add(closer);
        roster.addAll(bullpen);
        for (NpcPlayer n : mine) {
            if (!roster.contains(n)) {
                n.sendDown();
            }
        }
        return callUps;
    }

    /** 팜 점수로 유망주 생성: OVR = 56 + (팜 − 60)/6 ± 4, 21~24세, 잠재력 = OVR + 5~17 */
    static NpcPlayer callUp(Long worldId, WorldTeam team, Position position, RosterRole role, RandomGenerator rng) {
        double target = 56 + (team.getFarmScore() - 60) / 6.0 + rng.nextGaussian() * 4;
        int overall = (int) Math.round(Math.max(40, Math.min(80, target)));
        int[] attributes = WorldGenerator.attributes(rng, overall);
        Hand throwsHand = position.isPitcher() && rng.nextInt(100) < 28 ? Hand.L : Hand.R;
        Hand bats = position.isPitcher() ? throwsHand : (rng.nextInt(100) < 35 ? Hand.L : Hand.R);
        return NpcPlayer.create(worldId, team.getTeam().getId(), NameGenerator.next(rng), position, bats, throwsHand,
                21 + rng.nextInt(4), Math.min(99, overall + 5 + rng.nextInt(13)), attributes, role,
                Payroll.MINIMUM_SALARY, 1, 0);
    }

    private static List<NpcPlayer> sortedByOverall(List<NpcPlayer> players) {
        List<NpcPlayer> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparingDouble(NpcPlayer::getOverall).reversed());
        return sorted;
    }
}
