package com.mlbgame.season.transition;

import com.mlbgame.career.CareerService;
import com.mlbgame.common.Messages;
import com.mlbgame.contract.ContractService;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.standings.StandingsService;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.stats.LeagueAverageService;
import com.mlbgame.league.mode.LeagueRuleset;
import com.mlbgame.league.mode.LeagueRulesets;
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
import com.mlbgame.reputation.ReputationService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 전환: 정규시즌·포스트시즌이 끝난 뒤 다음 시즌으로 넘어간다.
 *
 * <ol>
 *   <li>남은 부상 결장 회복, 내 선수 나이 +1, 노화 하락 ({@link AgingRules}) — 노화로 훈련 비용이 줄어 진행도가 넘치면 바로 +1 정산</li>
 *   <li>45세가 되면 강제 은퇴 (슬롯 반납, 기록 보관)</li>
 *   <li>평판 시즌 결산 (수상·우승·메이저 풀타임) — 계약 제안에 반영되도록 계약보다 먼저</li>
 *   <li>계약 만료·연봉조정·FA 제안 ({@link ContractService}) — NPC FA 시장보다 먼저</li>
 *   <li>올해 메이저리그 평균 저장 (세부 지표 기준) → 리그 월드 변화 ({@link WorldEvolution}: NPC 성장·노화·은퇴·FA·콜업, 구단 예산·팜)</li>
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
    private final CareerService careerService;
    private final LeagueAverageService leagueAverageService;
    private final ReputationService reputationService;

    public SeasonTransitionService(PlayerService playerService, SeasonService seasonService,
                                   LeagueWorldService leagueWorldService, StandingsService standingsService,
                                   WorldTeamRepository worldTeamRepository, NpcPlayerRepository npcPlayerRepository,
                                   PostseasonRepository postseasonRepository, TeamRepository teamRepository,
                                   ContractService contractService, CareerService careerService,
                                   LeagueAverageService leagueAverageService, ReputationService reputationService) {
        this.contractService = contractService;
        this.careerService = careerService;
        this.leagueAverageService = leagueAverageService;
        this.reputationService = reputationService;
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
            throw new SeasonProgressException(Messages.get("offseason.error.notReady"));
        }
        LeagueWorld world = leagueWorldService.getOrCreate(playerId);
        int year = world.getCurrentYear();
        LeagueRuleset rules = LeagueRulesets.of(world);

        StandingsTable table = standingsService.standings(world, year, rules.seriesPerSeason(),
                player.getTeam().getId(), seasonService.mlbSeason(userId, playerId).teamResults());

        // 1~2. 내 선수 부상 회복·나이·노화·은퇴
        boolean wasInjured = season.isSidelined();
        season.recoverFromInjury();      // 새 시즌 행이 만들어지기 전(계약 협상 중)에도 부상으로 보이지 않게
        player.ageOneYear();
        Map<Attribute, Integer> aging = AgingRules.decline(player.getPosition(), player.getAge());
        List<String> trainingNews = player.applyAging(aging);
        boolean retired = player.getAge() >= AgingRules.FORCED_RETIREMENT_AGE;
        if (retired) {
            player.retire();
            careerService.recordRetirement(player);      // 랭킹 스냅샷
        }

        // 평판 시즌 결산 (수상·우승·메이저 풀타임) — 연장 제안·FA 오퍼에 반영되도록 계약보다 먼저
        boolean champion = !CareerService.championships(
                postseasonRepository.findByPlayerIdAndSeasonYear(playerId, year).stream().toList(),
                Map.of(year, season)).isEmpty();
        List<String> reputationNews = retired ? List.of() : reputationService.seasonReview(player, season, champion);

        // 3. 계약 (만료·연봉조정·FA·연장 제안) — NPC FA 시장보다 먼저 받아야 구단 예산이 남아 있다
        List<String> contractNews = retired ? List.of() : contractService.offseason(player, world, year);

        // 4. 리그 월드 변화 — 바뀌기 전에 올해 리그 평균(세부 지표 기준)을 남긴다
        leagueAverageService.snapshot(world, year);
        Map<Long, Double> winPct = new HashMap<>();
        table.rows().forEach(r -> winPct.put(r.team().getId(), r.pct()));
        List<WorldTeam> teams = worldTeamRepository.findByWorldIdOrderByIdAsc(world.getId());
        List<NpcPlayer> npcs = npcPlayerRepository.findByWorldIdAndStatusIn(world.getId(),
                List.of(NpcStatus.MLB, NpcStatus.MINORS, NpcStatus.FA));
        List<NpcPlayer> created = new ArrayList<>();
        Report report = WorldEvolution.evolve(seed(world.getSeed(), year + 1), world.getId(), teams, npcs, winPct, created,
                rules.payroll());
        npcPlayerRepository.saveAll(created);

        // 5. 다음 시즌
        world.advanceYear();
        List<String> lines = new ArrayList<>(headlines(player, report, aging, trainingNews, retired, year + 1, rules));
        lines.addAll(1, contractNews);
        lines.addAll(1, reputationNews);
        if (wasInjured && !retired) {
            lines.add(1, Messages.get("offseason.injuryRecovered"));
        }
        return new Result(year, year + 1, player.getAge(), aging, retired, lines);
    }

    static long seed(long worldSeed, int year) {
        return worldSeed * 53 + year * 1_009L;
    }

    private List<String> headlines(Player player, Report report, Map<Attribute, Integer> aging,
                                   List<String> trainingNews, boolean retired, int year, LeagueRuleset rules) {
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        List<String> lines = new ArrayList<>();
        if (retired) {
            lines.add(Messages.get("offseason.retired", player.getName(), player.getAge()));
        } else {
            lines.add(Messages.get("offseason.seasonStart", year, player.getName(), player.getAge()));
        }
        if (!aging.isEmpty()) {
            lines.add(Messages.get("offseason.aging", aging.entrySet().stream()
                    .map(e -> e.getKey().getLabel() + " " + e.getValue())
                    .collect(Collectors.joining(", "))));
        }
        lines.addAll(trainingNews);
        lines.add(Messages.get("offseason.leagueNews", report.retired(), report.freeAgents(), report.signings().size(),
                report.callUps()));
        for (Signing s : report.signings().stream().limit(3).toList()) {
            lines.add(Messages.get("offseason.bigSigning", s.name(), Math.round(s.overall()),
                    teams.get(s.teamId()).getDisplayName(), s.years(), rules.payroll().format(s.salary())));
        }
        return lines;
    }
}
