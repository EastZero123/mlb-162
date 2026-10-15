package com.mlbgame.trade;

import com.mlbgame.common.Dice;
import com.mlbgame.league.Team;
import com.mlbgame.league.standings.StandingsService;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.NpcPlayer;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.NpcStatus;
import com.mlbgame.league.world.WorldTeam;
import com.mlbgame.league.world.WorldTeamRepository;
import com.mlbgame.player.Player;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.trade.TradeEvent.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 트레이드 이벤트: 시즌 중 루머 → 성사, 유저의 트레이드 요청. 협상형 맞교환은 없다. 규칙: {@link TradeRules}
 * 트레이드되면 계약·레벨은 그대로, 다음 시리즈부터 새 구단 일정으로 뛴다.
 */
@Service
public class TradeService {

    private final StandingsService standingsService;
    private final WorldTeamRepository worldTeamRepository;
    private final NpcPlayerRepository npcPlayerRepository;
    private final TradeEventRepository repository;
    private final Dice dice;

    public TradeService(StandingsService standingsService, WorldTeamRepository worldTeamRepository,
                        NpcPlayerRepository npcPlayerRepository, TradeEventRepository repository, Dice dice) {
        this.standingsService = standingsService;
        this.worldTeamRepository = worldTeamRepository;
        this.npcPlayerRepository = npcPlayerRepository;
        this.repository = repository;
        this.dice = dice;
    }

    /**
     * 시리즈가 끝날 때 호출. 루머 또는 트레이드가 생기면 그 이벤트를 돌려준다.
     *
     * @param myMlbResults 내 선수가 메이저에서 뛴 시리즈별 팀 결과 (순위 계산용)
     */
    @Transactional
    public TradeEvent afterSeries(Player player, PlayerSeason season, int seriesNo, LeagueWorld world,
                                  Map<Integer, int[]> myMlbResults) {
        if (!TradeRules.inWindow(seriesNo, season.getLeagueLevel()) || season.isSidelined()) {
            return null;
        }
        Context ctx = context(player, season, seriesNo, world, myMlbResults);
        double chance = TradeRules.chance(ctx.winPct.getOrDefault(player.getTeam().getId(), 0.5), player.getOverall(),
                player.getSalary(), ctx.overBudget, requestDenied(player, season));
        if (dice.nextDouble() >= chance) {
            return null;
        }
        if (!rumorActive(player, season)) {
            return repository.save(new TradeEvent(player.getId(), season.getSeasonYear(), seriesNo, Kind.RUMOR,
                    player.getTeam().getId(), null, player.getTeam().getNameKo() + "가 " + player.getName()
                    + "의 트레이드를 검토 중이라는 소문이 돕니다"));
        }
        return execute(player, season, seriesNo, ctx, "트레이드 마감 전 전격 이적");
    }

    /** 유저의 트레이드 요청 (시즌당 1회, 마감 전까지) */
    @Transactional
    public TradeEvent request(Player player, PlayerSeason season, LeagueWorld world, Map<Integer, int[]> myMlbResults) {
        int seriesNo = season.getSeriesPlayed();
        if (seriesNo > TradeRules.DEADLINE_SERIES || season.isCompleted()) {
            throw new SeasonProgressException("트레이드 마감(36번째 시리즈)이 지났습니다.");
        }
        if (requestDenied(player, season)
                || repository.findByPlayerIdAndSeasonYearOrderByIdAsc(player.getId(), season.getSeasonYear()).stream()
                .anyMatch(e -> e.getKind() == Kind.TRADE)) {
            throw new SeasonProgressException("이번 시즌에는 더 이상 트레이드를 요청할 수 없습니다.");
        }
        Context ctx = context(player, season, Math.max(1, seriesNo), world, myMlbResults);
        if (dice.nextDouble() < TradeRules.REQUEST_SUCCESS) {
            return execute(player, season, seriesNo, ctx, "트레이드 요청 수용");
        }
        return repository.save(new TradeEvent(player.getId(), season.getSeasonYear(), seriesNo, Kind.REQUEST_DENIED,
                player.getTeam().getId(), null, "구단이 트레이드 요청을 거절했습니다. 관계가 틀어져 이후 트레이드될 가능성이 커졌습니다"));
    }

    @Transactional(readOnly = true)
    public List<TradeEvent> seasonEvents(Long playerId, int year) {
        return repository.findByPlayerIdAndSeasonYearOrderByIdAsc(playerId, year);
    }

    @Transactional(readOnly = true)
    public List<TradeEvent> eventsAfter(Long playerId, int year, int seriesNo) {
        return repository.findByPlayerIdAndSeasonYearAndAfterSeries(playerId, year, seriesNo);
    }

    @Transactional(readOnly = true)
    public List<TradeEvent> career(Long playerId) {
        return repository.findByPlayerIdOrderByIdAsc(playerId);
    }

    public boolean canRequest(Player player, PlayerSeason season) {
        return !season.isCompleted() && season.getSeriesPlayed() <= TradeRules.DEADLINE_SERIES
                && !requestDenied(player, season)
                && repository.findByPlayerIdAndSeasonYearOrderByIdAsc(player.getId(), season.getSeasonYear()).stream()
                .noneMatch(e -> e.getKind() == Kind.TRADE);
    }

    // ------------------------------------------------------------

    private TradeEvent execute(Player player, PlayerSeason season, int seriesNo, Context ctx, String note) {
        Long from = player.getTeam().getId();
        WorldTeam destination = destination(from, ctx);
        player.tradeTo(destination.getTeam());
        season.trade(destination.getTeam().getId());
        return repository.save(new TradeEvent(player.getId(), season.getSeasonYear(), seriesNo, Kind.TRADE, from,
                destination.getTeam().getId(), note + " — " + destination.getTeam().getNameKo() + " 유니폼을 입습니다"));
    }

    /** 경쟁 팀 중 (승률 − .5 + 예산 여유) 가중 무작위 */
    private WorldTeam destination(Long currentTeamId, Context ctx) {
        List<WorldTeam> candidates = new ArrayList<>(ctx.teams.stream()
                .filter(t -> !t.getTeam().getId().equals(currentTeamId))
                .filter(t -> ctx.winPct.getOrDefault(t.getTeam().getId(), 0.5) >= TradeRules.CONTENDER_PCT)
                .toList());
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(ctx.teams.stream().filter(t -> !t.getTeam().getId().equals(currentTeamId)).toList());
        }
        double total = 0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            WorldTeam t = candidates.get(i);
            double room = Math.max(0, t.getBudget() - ctx.payroll.getOrDefault(t.getTeam().getId(), 0L)) / 100_000_000.0;
            weights[i] = Math.max(0.05, ctx.winPct.getOrDefault(t.getTeam().getId(), 0.5) - 0.5 + room);
            total += weights[i];
        }
        double roll = dice.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights[i];
            if (roll <= 0) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    private boolean rumorActive(Player player, PlayerSeason season) {
        List<TradeEvent> events = repository.findByPlayerIdAndSeasonYearOrderByIdAsc(player.getId(), season.getSeasonYear());
        boolean rumor = events.stream().anyMatch(e -> e.getKind() == Kind.RUMOR);
        boolean traded = events.stream().anyMatch(e -> e.getKind() == Kind.TRADE);
        return rumor && !traded;
    }

    private boolean requestDenied(Player player, PlayerSeason season) {
        return repository.findByPlayerIdAndSeasonYearOrderByIdAsc(player.getId(), season.getSeasonYear()).stream()
                .anyMatch(e -> e.getKind() == Kind.REQUEST_DENIED);
    }

    private record Context(List<WorldTeam> teams, Map<Long, Double> winPct, Map<Long, Long> payroll, boolean overBudget) {
    }

    private Context context(Player player, PlayerSeason season, int seriesNo, LeagueWorld world,
                            Map<Integer, int[]> myMlbResults) {
        StandingsTable table = standingsService.standings(world, season.getSeasonYear(), seriesNo,
                player.getTeam().getId(), myMlbResults);
        Map<Long, Double> winPct = new HashMap<>();
        table.rows().forEach(r -> winPct.put(r.team().getId(), r.pct()));
        Map<Long, Long> payroll = new HashMap<>();
        for (NpcPlayer npc : npcPlayerRepository.findByWorldIdAndStatus(world.getId(), NpcStatus.MLB)) {
            payroll.merge(npc.getTeamId(), npc.getSalary(), Long::sum);
        }
        List<WorldTeam> teams = worldTeamRepository.findByWorldIdOrderByIdAsc(world.getId());
        Team current = player.getTeam();
        long myBudget = teams.stream().filter(t -> t.getTeam().getId().equals(current.getId()))
                .mapToLong(WorldTeam::getBudget).findFirst().orElse(Long.MAX_VALUE);
        boolean overBudget = payroll.getOrDefault(current.getId(), 0L) + player.getSalary() > myBudget;
        return new Context(teams, winPct, payroll, overBudget);
    }
}
