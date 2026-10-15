package com.mlbgame.season.transition;

import com.mlbgame.contract.ContractService;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.standings.StandingsService;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.league.world.NpcPlayer;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.NpcStatus;
import com.mlbgame.league.world.WorldEvolution;
import com.mlbgame.league.world.WorldEvolution.Report;
import com.mlbgame.league.world.WorldEvolution.Signing;
import com.mlbgame.league.world.WorldTeam;
import com.mlbgame.league.world.WorldTeamRepository;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.postseason.PostseasonRepository;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 전환: 정규시즌·포스트시즌이 끝난 뒤 다음 시즌으로 넘어간다.
 *
 * <ol>
 *   <li>리그 월드 변화 ({@link WorldEvolution}: NPC 성장·노화·은퇴·FA·콜업, 구단 예산·팜)</li>
 *   <li>내 선수 나이 +1, 노화 하락 ({@link AgingRules})</li>
 *   <li>45세가 되면 강제 은퇴 (슬롯 반납, 기록 보관)</li>
 *   <li>월드 연도 +1 → 다음 화면 진입 때 새 시즌이 1번 시리즈부터 시작 (소속 레벨·구단 유지)</li>
 * </ol>
 */
@Service
public class SeasonTransitionService {

    private final PlayerService playerService;
    private final SeasonService seasonService;
    private final LeagueWorldService leagueWorldService;
    private final StandingsService standingsService;
    private final WorldTeamRepository worldTeamRepository;
    private final NpcPlayerRepository npcPlayerRepository;
    private final PostseasonRepository postseasonRepository;
    private final TeamRepository teamRepository;
    private final ContractService contractService;

    public SeasonTransitionService(PlayerService playerService, SeasonService seasonService,
                                   LeagueWorldService leagueWorldService, StandingsService standingsService,
                                   WorldTeamRepository worldTeamRepository, NpcPlayerRepository npcPlayerRepository,
                                   PostseasonRepository postseasonRepository, TeamRepository teamRepository,
                                   ContractService contractService) {
        this.contractService = contractService;
        this.playerService = playerService;
        this.seasonService = seasonService;
        this.leagueWorldService = leagueWorldService;
        this.standingsService = standingsService;
        this.worldTeamRepository = worldTeamRepository;
        this.npcPlayerRepository = npcPlayerRepository;
        this.postseasonRepository = postseasonRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * @param headlines 화면에 보여줄 요약 문장들
     */
    public record Result(int fromYear, int toYear, int newAge, Map<Attribute, Integer> aging, boolean retired,
                         List<String> headlines) {
    }

    /** 다음 시즌으로 넘어갈 수 있는 상태인가 */
    @Transactional(readOnly = true)
    public boolean ready(Long playerId, PlayerSeason season) {
        return season.isCompleted() && !season.isTrainingPending()
                && postseasonRepository.findByPlayerIdAndSeasonYear(playerId, season.getSeasonYear())
                .map(p -> p.isDone()).orElse(false);
    }

    @Transactional
    public Result advance(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        PlayerSeason season = seasonService.currentSeason(userId, playerId);
        if (!ready(playerId, season)) {
            throw new SeasonProgressException("정규시즌·마지막 훈련·포스트시즌을 모두 마친 뒤 다음 시즌으로 넘어갈 수 있습니다.");
        }
        LeagueWorld world = leagueWorldService.getOrCreate(playerId);
        int year = world.getCurrentYear();

        // 1. 리그 월드 변화
        StandingsTable table = standingsService.standings(world, year, PlayerSeason.SERIES_PER_SEASON,
                player.getTeam().getId(), seasonService.mlbSeason(userId, playerId).teamResults());
        Map<Long, Double> winPct = new HashMap<>();
        table.rows().forEach(r -> winPct.put(r.team().getId(), r.pct()));
        List<WorldTeam> teams = worldTeamRepository.findByWorldIdOrderByIdAsc(world.getId());
        List<NpcPlayer> npcs = npcPlayerRepository.findByWorldIdAndStatusIn(world.getId(),
                List.of(NpcStatus.MLB, NpcStatus.MINORS, NpcStatus.FA));
        List<NpcPlayer> created = new ArrayList<>();
        Report report = WorldEvolution.evolve(seed(world.getSeed(), year + 1), world.getId(), teams, npcs, winPct, created);
        npcPlayerRepository.saveAll(created);

        // 2~3. 내 선수 나이·노화·은퇴
        player.ageOneYear();
        Map<Attribute, Integer> aging = AgingRules.decline(player.getPosition(), player.getAge());
        player.applyAging(aging);
        boolean retired = player.getAge() >= AgingRules.FORCED_RETIREMENT_AGE;
        if (retired) {
            player.retire();
        }

        // 계약 (만료·연봉조정·FA·연장 제안)
        List<String> contractNews = retired ? List.of() : contractService.offseason(player, world, year);

        // 4. 다음 시즌
        world.advanceYear();
        List<String> lines = new ArrayList<>(headlines(player, report, aging, retired, year + 1));
        lines.addAll(1, contractNews);
        return new Result(year, year + 1, player.getAge(), aging, retired, lines);
    }

    static long seed(long worldSeed, int year) {
        return worldSeed * 53 + year * 1_009L;
    }

    private List<String> headlines(Player player, Report report, Map<Attribute, Integer> aging, boolean retired, int year) {
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        NumberFormat money = NumberFormat.getIntegerInstance(Locale.US);
        List<String> lines = new ArrayList<>();
        if (retired) {
            lines.add(player.getName() + " 선수가 " + player.getAge() + "세로 은퇴합니다. 그동안 수고 많았습니다!");
        } else {
            lines.add(year + " 시즌 시작! " + player.getName() + " " + player.getAge() + "세");
        }
        if (!aging.isEmpty()) {
            lines.add("노화: " + aging.entrySet().stream()
                    .map(e -> e.getKey().getLabel() + " " + e.getValue())
                    .collect(Collectors.joining(", ")));
        }
        lines.add("리그 소식: 은퇴 " + report.retired() + "명 · FA " + report.freeAgents() + "명 · FA 계약 "
                + report.signings().size() + "건 · 유망주 콜업 " + report.callUps() + "명");
        for (Signing s : report.signings().stream().limit(3).toList()) {
            lines.add("대형 계약: " + s.name() + " (OVR " + Math.round(s.overall()) + ") → "
                    + teams.get(s.teamId()).getNameKo() + " " + s.years() + "년 $" + money.format(s.salary()) + "/년");
        }
        return lines;
    }
}
