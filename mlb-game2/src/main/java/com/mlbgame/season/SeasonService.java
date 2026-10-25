package com.mlbgame.season;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlbgame.award.Award;
import com.mlbgame.award.AwardService;
import com.mlbgame.common.Messages;
import com.mlbgame.career.HallOfFameService;
import com.mlbgame.contract.ContractService;
import com.mlbgame.trade.TradeEvent;
import com.mlbgame.trade.TradeRules;
import com.mlbgame.trade.TradeService;
import com.mlbgame.league.mode.LeagueRuleset;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.PlayerStage;
import com.mlbgame.season.SeriesRecorder.PlayedSeries;
import com.mlbgame.season.earnings.EarningType;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.season.promotion.LevelChange;
import com.mlbgame.season.promotion.LevelChangeRepository;
import com.mlbgame.season.promotion.PromotionRules;
import com.mlbgame.season.promotion.PromotionRules.Decision;
import com.mlbgame.season.schedule.SeasonScheduleService;
import com.mlbgame.season.schedule.SeasonScheduleService.Matchup;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.series.SeriesInput;
import com.mlbgame.simulation.series.SeriesResult;
import com.mlbgame.simulation.series.SeriesSimulator;
import com.mlbgame.league.LeagueLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시즌 진행: 시즌 시작, 다음 시리즈 진행(엔진 실행 → 기록 저장 → 시즌 누적 → 연봉 적립).
 *
 * <p>규칙
 * <ul>
 *   <li>훈련을 고르기 전에는 다음 시리즈를 진행할 수 없다 (훈련은 M3-4)</li>
 *   <li>부상 결장 중에도 시리즈는 진행된다 — 팀 경기만 치르고 내 선수 기록은 쌓이지 않는다</li>
 *   <li>시리즈마다 소속 레벨 연봉의 1/54 을 수령 원장에 적립</li>
 * </ul>
 */
@Service
public class SeasonService {

    private final PlayerService playerService;
    private final LeagueWorldService leagueWorldService;
    private final SeasonScheduleService scheduleService;
    private final PlayerSeasonRepository seasonRepository;
    private final SeriesRecorder seriesRecorder;
    private final SeriesRecordRepository seriesRecordRepository;
    private final LevelChangeRepository levelChangeRepository;
    private final AwardService awardService;
    private final ContractService contractService;
    private final TradeService tradeService;
    private final EarningsService earningsService;
    private final HallOfFameService hallOfFameService;
    private final ObjectMapper objectMapper;

    public SeasonService(PlayerService playerService, LeagueWorldService leagueWorldService,
                         SeasonScheduleService scheduleService, PlayerSeasonRepository seasonRepository,
                         SeriesRecorder seriesRecorder, SeriesRecordRepository seriesRecordRepository,
                         LevelChangeRepository levelChangeRepository, AwardService awardService,
                         ContractService contractService, TradeService tradeService, EarningsService earningsService,
                         HallOfFameService hallOfFameService, ObjectMapper objectMapper) {
        this.awardService = awardService;
        this.tradeService = tradeService;
        this.contractService = contractService;
        this.playerService = playerService;
        this.leagueWorldService = leagueWorldService;
        this.scheduleService = scheduleService;
        this.seasonRepository = seasonRepository;
        this.seriesRecorder = seriesRecorder;
        this.seriesRecordRepository = seriesRecordRepository;
        this.levelChangeRepository = levelChangeRepository;
        this.earningsService = earningsService;
        this.hallOfFameService = hallOfFameService;
        this.objectMapper = objectMapper;
    }

    /** 현재 시즌 (없으면 시작). 드래프트를 마친 선수만 */
    @Transactional
    public PlayerSeason currentSeason(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        player.requireStage(PlayerStage.DRAFTED);
        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());
        return seasonRepository.findByPlayerIdAndSeasonYear(player.getId(), world.getCurrentYear())
                .orElseGet(() -> seasonRepository.save(PlayerSeason.start(player.getId(), world.getCurrentYear(),
                        player.getLeagueLevel(), player.getTeam().getId())));
    }

    /** 이번 시즌 일정 + 양 팀 전력 (시즌이 없으면 시작하므로 쓰기 트랜잭션) */
    @Transactional
    public List<Matchup> matchups(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        PlayerSeason season = currentSeason(userId, playerId);
        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());
        return scheduleService.matchups(world, season.getSeasonYear(), player.getTeam(), season.getLeagueLevel());
    }

    @Transactional
    public SeriesOutcome playNextSeries(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        if (player.isRetired()) {
            throw new SeasonProgressException(Messages.get("error.retired"));
        }
        PlayerSeason season = currentSeason(userId, playerId);
        if (season.isCompleted()) {
            throw new SeasonProgressException(Messages.get("season.error.scheduleDone"));
        }
        if (season.isTrainingPending()) {
            throw new SeasonProgressException(Messages.get("season.error.trainFirst"));
        }
        if (contractService.hasPending(playerId)) {
            throw new SeasonProgressException(Messages.get("season.error.contractPending"));
        }

        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());
        LeagueRuleset rules = LeagueRulesets.of(player);
        int seriesNo = season.getNextSeriesNo();
        Matchup matchup = scheduleService.matchups(world, season.getSeasonYear(), player.getTeam(), season.getLeagueLevel())
                .get(seriesNo - 1);
        boolean sidelined = season.isSidelined();

        SeriesInput input = new SeriesInput(SeriesRecorder.newSeed(), season.getLeagueLevel(), matchup.series().home(),
                matchup.team(), matchup.opponentRatings(), matchup.series().games(), PlayerSnapshot.of(player),
                season.getFatigue(), season.usage(), sidelined);
        PlayedSeries played = seriesRecorder.play(player.getId(), season.getSeasonYear(), seriesNo,
                player.getTeam().getId(), input);
        SeriesResult result = played.result();

        BattingLine batting = read(season.getBattingJson(), BattingLine.class, BattingLine::new);
        PitchingLine pitching = read(season.getPitchingJson(), PitchingLine.class, PitchingLine::new);
        FieldingLine fielding = read(season.getFieldingJson(), FieldingLine.class, FieldingLine::new);
        if (result.batting() != null) batting.add(result.batting());
        if (result.pitching() != null) pitching.add(result.pitching());
        if (result.fielding() != null) fielding.add(result.fielding());

        SeriesSimulator.CarryOver next = SeriesSimulator.carryOver(result, player.attribute(Attribute.DURABILITY));
        season.applySeries(result, next, sidelined, rules.seriesPerSeason(),
                player.isPitcher() ? null : write(batting),
                player.isPitcher() ? write(pitching) : null,
                player.isPitcher() ? null : write(fielding));
        if (!player.hasTrainableAttribute()) {
            season.completeTraining();          // 능력치가 모두 100이면 훈련할 게 없으니 바로 다음 시리즈로
        }

        contractService.ensureMlbContract(player, season.getLeagueLevel());   // 메이저 첫 진입 → 최저 연봉 계약
        earningsService.add(player.getId(), season.getSeasonYear(), EarningType.SALARY,
                ContractService.seriesSalary(player, season.getLeagueLevel()));

        // 시상은 이번 달을 뛴 레벨 기준으로 먼저 판정하고, 그다음 승격·강등
        List<Award> awards = awardService.evaluateAfterSeries(player, season, seriesNo, season.getLeagueLevel(), world);
        LevelChange levelChange = evaluatePromotion(player, season, seriesNo, rules);
        TradeEvent trade = TradeRules.inWindow(seriesNo, rules.tradeEligible(season.getLeagueLevel()))
                ? tradeService.afterSeries(player, season, seriesNo, world, mlbSeason(userId, playerId).teamResults())
                : null;
        return new SeriesOutcome(season, matchup, played.record(), result, sidelined, levelChange, awards, trade);
    }

    /**
     * 유저의 트레이드 요청. 선수·시즌을 이 트랜잭션 안에서 불러와야 이적(구단 변경)이 저장된다
     * (open-in-view 가 꺼져 있어 컨트롤러에서 불러온 엔티티는 준영속 상태).
     */
    @Transactional
    public TradeEvent requestTrade(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        if (player.isRetired()) {
            throw new SeasonProgressException(Messages.get("error.retired"));
        }
        PlayerSeason season = currentSeason(userId, playerId);
        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());
        return tradeService.request(player, season, world, mlbSeason(userId, playerId).teamResults());
    }

    /** 판정 주기(MLB 9시리즈)마다 승격·강등 판정 (부상 결장 중이면 판정하지 않음, 레전드는 최상위 레벨 강등 면제) */
    private LevelChange evaluatePromotion(Player player, PlayerSeason season, int seriesNo, LeagueRuleset rules) {
        PromotionRules.Params params = rules.promotion();
        if (season.isCompleted() || season.isSidelined()
                || !PromotionRules.isEvaluationPoint(params, seriesNo, rules.seriesPerSeason())) {
            return null;
        }
        BattingLine recentBatting = new BattingLine();
        PitchingLine recentPitching = new PitchingLine();
        seriesRecordRepository.findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(player.getId(), season.getSeasonYear())
                .stream()
                .filter(r -> r.getSeriesNo() > seriesNo - params.interval())
                .map(seriesRecorder::readSummary)
                .forEach(summary -> {
                    if (summary.batting() != null) recentBatting.add(summary.batting());
                    if (summary.pitching() != null) recentPitching.add(summary.pitching());
                });

        LeagueLevel level = season.getLeagueLevel();
        Decision decision = PromotionRules.evaluate(params, level, rules.above(level), rules.below(level), player.getOverall(),
                player.getPosition(), recentBatting, recentPitching);
        if (!decision.moved()) {
            return null;
        }
        // 점수 계산은 커리어 전체 기록을 읽으므로 메이저 강등 판정이 나왔을 때만 한다
        if (decision.verdict() == PromotionRules.Verdict.DEMOTE
                && PromotionRules.isDemotionExempt(params, decision.from(), hallOfFameService.currentScore(player))) {
            return null;
        }
        player.moveToLevel(decision.to());
        season.changeLevel(decision.to());
        return levelChangeRepository.save(new LevelChange(player.getId(), season.getSeasonYear(), seriesNo,
                decision.from(), decision.to(), decision.score()));
    }

    // ------------------------------------------------------------ 화면 조회

    /** 시리즈 상세: 저장된 입력으로 다시 계산 (하이라이트·경기별 기록은 저장하지 않으므로) */
    @Transactional
    public SeriesDetail seriesDetail(Long userId, Long playerId, int seriesNo) {
        PlayerSeason season = currentSeason(userId, playerId);
        SeriesRecord record = seriesRecordRepository
                .findByPlayerIdAndSeasonYearAndSeriesNo(playerId, season.getSeasonYear(), seriesNo)
                .orElseThrow(() -> new SeasonProgressException(Messages.get("season.error.noSeries", seriesNo)));
        SeriesInput input = seriesRecorder.readInput(record);
        Matchup matchup = matchups(userId, playerId).get(seriesNo - 1);
        LevelChange levelChange = levelChangeRepository
                .findByPlayerIdAndSeasonYearAndAfterSeries(playerId, season.getSeasonYear(), seriesNo).orElse(null);
        List<Award> awards = awardService.awardsAfter(playerId, season.getSeasonYear(), seriesNo);
        List<TradeEvent> trades = tradeService.eventsAfter(playerId, season.getSeasonYear(), seriesNo);
        return new SeriesDetail(season, record, input, seriesRecorder.replay(record.getId()), matchup, levelChange,
                awards, trades);
    }

    /**
     * 이번 시즌 내 선수가 메이저리그에서 뛴 기록만 모은 것 — 메이저 리더보드·순위표에 쓴다.
     *
     * @param teamResults 메이저 시리즈 번호 → 팀 [승, 패]
     */
    public record MlbSeason(BattingLine batting, PitchingLine pitching, Map<Integer, int[]> teamResults) {
        public boolean played() {
            return !teamResults.isEmpty();
        }
    }

    @Transactional
    public MlbSeason mlbSeason(Long userId, Long playerId) {
        PlayerSeason season = currentSeason(userId, playerId);
        BattingLine batting = new BattingLine();
        PitchingLine pitching = new PitchingLine();
        Map<Integer, int[]> results = new HashMap<>();
        for (SeriesRecord record : seriesRecordRepository
                .findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(playerId, season.getSeasonYear())) {
            if (!record.getLeagueLevel().isTop()) {
                continue;
            }
            SeriesSummary summary = seriesRecorder.readSummary(record);
            if (summary.batting() != null) batting.add(summary.batting());
            if (summary.pitching() != null) pitching.add(summary.pitching());
            results.put(record.getSeriesNo(), new int[]{record.getWins(), record.getLosses()});
        }
        return new MlbSeason(batting, pitching, results);
    }

    @Transactional(readOnly = true)
    public List<LevelChange> levelHistory(Long playerId) {
        return levelChangeRepository.findByPlayerIdOrderByIdAsc(playerId);
    }

    /** 시즌 시리즈 목록 (요약만 — 재계산 없이 빠르게) */
    @Transactional
    public List<SeriesLogRow> seriesLog(Long userId, Long playerId) {
        PlayerSeason season = currentSeason(userId, playerId);
        List<Matchup> matchups = matchups(userId, playerId);
        return seriesRecordRepository.findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(playerId, season.getSeasonYear())
                .stream()
                .map(r -> new SeriesLogRow(r, matchups.get(r.getSeriesNo() - 1), seriesRecorder.readSummary(r)))
                .toList();
    }

    public record SeriesDetail(PlayerSeason season, SeriesRecord record, SeriesInput input, SeriesResult result,
                               Matchup matchup, LevelChange levelChange, List<Award> awards, List<TradeEvent> trades) {
        public boolean sidelined() {
            return input.sidelined();
        }
    }

    public record SeriesLogRow(SeriesRecord record, Matchup matchup, SeriesSummary summary) {
        /** 내 선수 시리즈 요약 한 줄 */
        public String mySummary() {
            if (summary.pitching() != null && summary.pitching().games > 0) {
                return Messages.get("line.games", summary.pitching().games, summary.pitching().summary());
            }
            if (summary.batting() != null && summary.batting().games > 0) {
                return summary.batting().summary();
            }
            return Messages.get("line.didNotPlay");
        }
    }

    // ------------------------------------------------------------ 시즌 누적 기록

    public BattingLine batting(PlayerSeason season) {
        return read(season.getBattingJson(), BattingLine.class, BattingLine::new);
    }

    public PitchingLine pitching(PlayerSeason season) {
        return read(season.getPitchingJson(), PitchingLine.class, PitchingLine::new);
    }

    private <T> T read(String json, Class<T> type, Supplier<T> empty) {
        if (json == null || json.isBlank()) {
            return empty.get();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("시즌 기록을 읽을 수 없습니다.", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("시즌 기록 저장 실패", e);
        }
    }

    /**
     * 시리즈 진행 결과.
     *
     * @param sidelined   부상 결장 시리즈였는지
     * @param levelChange 이 시리즈 뒤 승격·강등 (없으면 null)
     * @param awards      이 시리즈 뒤 받은 상
     * @param trade       이 시리즈 뒤 트레이드 루머·성사 (없으면 null)
     */
    public record SeriesOutcome(PlayerSeason season, Matchup matchup, SeriesRecord record, SeriesResult result,
                                boolean sidelined, LevelChange levelChange, List<Award> awards, TradeEvent trade) {
    }
}
