package com.mlbgame.league.stats;

import com.mlbgame.league.mode.LeagueRuleset;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.standings.StandingsGenerator;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.NpcPlayer;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.NpcStatus;
import com.mlbgame.league.world.RosterRole;
import com.mlbgame.league.world.TeamStrengthService;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.stats.NpcStatGenerator;
import com.mlbgame.simulation.stats.NpcStatGenerator.HitterLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메이저리그 NPC 선수 기록 (월 단위 통계 생성, 저장 없음 — 월드 시드·연도·월·선수로 같은 결과).
 * 월 = 9시리즈(27경기). 리더보드는 지난 달까지 집계한 기록 기준이다.
 */
@Service
public class LeagueStatsService {

    public static final int GAMES_PER_MONTH = 27;
    public static final int FIRST_MONTH = 4;

    private final NpcPlayerRepository npcPlayerRepository;
    private final TeamRepository teamRepository;
    private final TeamStrengthService teamStrengthService;
    private final NpcStatGenerator generator;

    public LeagueStatsService(NpcPlayerRepository npcPlayerRepository, TeamRepository teamRepository,
                              TeamStrengthService teamStrengthService, NpcStatGenerator generator) {
        this.npcPlayerRepository = npcPlayerRepository;
        this.teamRepository = teamRepository;
        this.teamStrengthService = teamStrengthService;
        this.generator = generator;
    }

    /** 선수 한 명의 기간 기록 (타자면 batting/fielding, 투수면 pitching) */
    public record PlayerLine(NpcPlayer npc, Team team, BattingLine batting, FieldingLine fielding, PitchingLine pitching) {
        public boolean isPitcher() {
            return npc.getPosition().isPitcher();
        }
    }

    /** months 에 들어 있는 월(4~9)의 기록 합계 */
    @Transactional(readOnly = true)
    public List<PlayerLine> lines(LeagueWorld world, int year, List<Integer> months) {
        List<NpcPlayer> npcs = npcPlayerRepository.findByWorldIdAndStatus(world.getId(), NpcStatus.MLB);
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, Double> winPct = new HashMap<>();
        LeagueRuleset rules = LeagueRulesets.of(world);
        NpcStatGenerator generator = this.generator.forMode(world.getLeagueMode());
        teamStrengthService.ratingsByTeam(world.getId(), rules.topLevel())
                .forEach((id, r) -> winPct.put(id, StandingsGenerator.winProbability(r.overall(), 65)));
        Map<Long, Integer> rotationSlots = rotationSlots(npcs);

        List<PlayerLine> result = new ArrayList<>();
        for (NpcPlayer npc : npcs) {
            BattingLine batting = new BattingLine();
            FieldingLine fielding = new FieldingLine();
            PitchingLine pitching = new PitchingLine();
            for (int month : months) {
                int games = rules.gamesInMonth(month - FIRST_MONTH + 1);
                SplittableRandom rng = new SplittableRandom(seed(world.getSeed(), year, month, npc.getId()));
                if (npc.getPosition().isPitcher()) {
                    pitching.add(generator.pitcher(npc.snapshot(), npc.getRosterRole(),
                            rotationSlots.getOrDefault(npc.getId(), 0), games,
                            winPct.getOrDefault(npc.getTeamId(), 0.5), rng));
                } else {
                    HitterLine line = generator.hitter(npc.snapshot(), npc.getRosterRole(), games, rng);
                    batting.add(line.batting());
                    fielding.add(line.fielding());
                }
            }
            result.add(new PlayerLine(npc, teams.get(npc.getTeamId()), batting, fielding, pitching));
        }
        return result;
    }

    /** 시즌 시작부터 completedMonths 개월 (0이면 빈 목록) */
    public static List<Integer> monthsThrough(int completedMonths) {
        List<Integer> months = new ArrayList<>();
        for (int i = 0; i < completedMonths; i++) {
            months.add(FIRST_MONTH + i);
        }
        return months;
    }

    static long seed(long worldSeed, int year, int month, Long npcId) {
        return worldSeed * 1_000_003L + year * 97L + month * 13L + npcId * 7_919L;
    }

    /** 구단별 선발을 OVR 순으로 1~5선발 */
    private static Map<Long, Integer> rotationSlots(List<NpcPlayer> npcs) {
        Map<Long, Integer> slots = new HashMap<>();
        npcs.stream()
                .filter(n -> n.getRosterRole() == RosterRole.ROTATION)
                .collect(Collectors.groupingBy(NpcPlayer::getTeamId))
                .values()
                .forEach(rotation -> {
                    List<NpcPlayer> sorted = new ArrayList<>(rotation);
                    sorted.sort(Comparator.comparingDouble(NpcPlayer::getOverall).reversed());
                    for (int i = 0; i < sorted.size(); i++) {
                        slots.put(sorted.get(i).getId(), i + 1);
                    }
                });
        return slots;
    }
}
