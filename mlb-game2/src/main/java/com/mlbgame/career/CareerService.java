package com.mlbgame.career;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.award.Award;
import com.mlbgame.combine.CombineSummary;
import com.mlbgame.combine.CombineSummaryRepository;
import com.mlbgame.common.Messages;
import com.mlbgame.player.PlayerNotFoundException;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.ranking.RankingEntry;
import com.mlbgame.ranking.RankingEntryRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.mlbgame.award.AwardService;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.league.world.LeagueWorldRepository;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.postseason.Postseason;
import com.mlbgame.postseason.PostseasonRepository;
import com.mlbgame.postseason.PostseasonService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.PlayerSeasonRepository;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
import com.mlbgame.season.SeasonMetrics;
import com.mlbgame.season.SeasonSplits;
import com.mlbgame.season.SeriesRecord;
import com.mlbgame.season.SeriesRecordRepository;
import com.mlbgame.season.SeriesRecorder;
import com.mlbgame.season.SeriesSummary;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.season.promotion.LevelChange;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.trade.TradeEvent;
import com.mlbgame.trade.TradeService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커리어 요약과 은퇴. 은퇴한 선수도 기록은 영구 보관되고, 커리어 화면에서 볼 수 있다.
 *
 * <pre>
 * 명예의 전당 점수
 *   타자 = 메이저 안타/20 + 홈런/5 + 수상 포인트 × 3 + 메이저 주요 시즌(27시리즈 이상) × 2 + 우승 × 5
 *   투수 = 메이저 승 × 2 + 세이브/2 + 탈삼진/30 + 수상 포인트 × 3 + 메이저 주요 시즌 × 2 + 우승 × 5
 *   안타·홈런·승·세이브·탈삼진 항목은 162 / 시즌 경기 수 를 곱한다 (KBO 144경기 → ×1.125)
 *   150 이상 "명예의 전당 헌액" / 100 이상 "명예의 전당 후보"
 *
 * 월드시리즈 우승: 메이저 소속으로 정규시즌을 마친 팀이 그해 포스트시즌 우승 (시즌 중 트레이드면 마친 팀 기준)
 * </pre>
 */
@Service
public class CareerService {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    public static final int HALL_OF_FAME = 150;
    public static final int HALL_OF_FAME_CANDIDATE = 100;
    static final int MAJOR_SEASON_SERIES = 27;
    static final int CHAMPIONSHIP_POINTS = 5;

    private final SeasonSplits seasonSplits;
    private final SeasonMetrics seasonMetrics;
    private final LeagueWorldRepository leagueWorldRepository;
    private final PostseasonRepository postseasonRepository;
    private final PostseasonService postseasonService;
    private final PlayerService playerService;
    private final SeasonService seasonService;
    private final PlayerSeasonRepository seasonRepository;
    private final SeriesRecordRepository seriesRecordRepository;
    private final SeriesRecorder seriesRecorder;
    private final AwardService awardService;
    private final TradeService tradeService;
    private final EarningsService earningsService;
    private final TeamRepository teamRepository;
    private final RankingEntryRepository rankingRepository;
    private final CombineSummaryRepository combineSummaryRepository;
    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;

    public CareerService(PlayerService playerService, SeasonService seasonService,
                         PlayerSeasonRepository seasonRepository, SeriesRecordRepository seriesRecordRepository,
                         SeriesRecorder seriesRecorder, AwardService awardService, TradeService tradeService,
                         EarningsService earningsService, TeamRepository teamRepository,
                         RankingEntryRepository rankingRepository, CombineSummaryRepository combineSummaryRepository,
                         UserRepository userRepository, PlayerRepository playerRepository, SeasonSplits seasonSplits,
                         PostseasonRepository postseasonRepository, PostseasonService postseasonService,
                         SeasonMetrics seasonMetrics, LeagueWorldRepository leagueWorldRepository) {
        this.seasonSplits = seasonSplits;
        this.seasonMetrics = seasonMetrics;
        this.leagueWorldRepository = leagueWorldRepository;
        this.postseasonRepository = postseasonRepository;
        this.postseasonService = postseasonService;
        this.rankingRepository = rankingRepository;
        this.combineSummaryRepository = combineSummaryRepository;
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
        this.seasonService = seasonService;
        this.seasonRepository = seasonRepository;
        this.seriesRecordRepository = seriesRecordRepository;
        this.seriesRecorder = seriesRecorder;
        this.awardService = awardService;
        this.tradeService = tradeService;
        this.earningsService = earningsService;
        this.teamRepository = teamRepository;
    }

    /** 자발적 은퇴 → 랭킹 스냅샷 저장 */
    @Transactional
    public Player retire(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        if (player.isRetired()) {
            throw new SeasonProgressException(Messages.get("error.alreadyRetired"));
        }
        player.retire();
        recordRetirement(player);
        return player;
    }

    /**
     * 은퇴 순간의 커리어를 랭킹 스냅샷으로 저장 (선수당 1회, 이후 변경 없음). 45세 강제 은퇴에서도 호출한다.
     */
    @Transactional
    public RankingEntry recordRetirement(Player player) {
        return rankingRepository.findByPlayerId(player.getId()).orElseGet(() -> {
            Summary summary = summaryOf(player);
            String nickname = userRepository.findById(player.getUserId()).map(User::getNickname).orElse("-");
            double combine = combineSummaryRepository.findById(player.getId()).map(CombineSummary::getTotalScore).orElse(0.0);
            RankingEntry entry = RankingEntry.of(player.getId(), player.getUserId(), nickname, player.getName(),
                            player.getPosition(), player.getAge(), summary.seasons().size(), LocalDateTime.now(KOREA))
                    .mode(player.getLeagueMode())
                    .career(summary.careerEarnings(), summary.hallOfFameScore(), summary.awardPoints(), combine);
            if (player.isPitcher()) {
                PitchingLine p = summary.mlbPitching();
                entry.pitching(p.games, p.outs, p.wins, p.strikeouts, p.saves, p.getEra());
            } else {
                BattingLine b = summary.mlbBatting();
                entry.batting(b.games, b.plateAppearances, b.hits, b.homeRuns, b.getAverage());
            }
            return rankingRepository.save(entry);
        });
    }

    public record SeasonRow(PlayerSeason season, Team team, BattingLine batting, PitchingLine pitching) {
    }

    /**
     * @param records           정규시즌 기록표 (메이저·마이너, 연도·레벨·구단별)
     * @param postseason        포스트시즌 기록 (연도별 + 통산, 내 선수가 직접 뛴 해만)
     * @param championshipYears 월드시리즈 우승 연도
     * @param detail            세부 지표 표 (메이저·마이너, records 와 같은 행 구성)
     */
    public record Summary(Player player, List<SeasonRow> seasons, BattingLine mlbBatting, PitchingLine mlbPitching,
                          int majorSeasons, List<Award> awards, int awardPoints, long careerEarnings,
                          List<LevelChange> levelHistory, List<TradeEvent> trades, int hallOfFameScore,
                          SeasonSplits.Tables records, List<SeasonSplits.Row> postseason,
                          List<Integer> championshipYears, SeasonMetrics.DetailTables detail) {

        /** 메이저 통산 WAR (메이저 기록이 없으면 null) */
        public Double getCareerWar() {
            if (detail.mlb().isEmpty()) {
                return null;
            }
            SeasonMetrics.DetailRow career = detail.mlb().get(detail.mlb().size() - 1);
            return career.hitter() != null ? career.hitter().war() : career.pitcher().war();
        }

        public int getChampionships() {
            return championshipYears.size();
        }

        public String getHallOfFameLabel() {
            if (hallOfFameScore >= HALL_OF_FAME) return Messages.get("career.hof.inducted");
            if (hallOfFameScore >= HALL_OF_FAME_CANDIDATE) return Messages.get("career.hof.candidate");
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Summary summary(Long userId, Long playerId) {
        return summaryOf(playerService.getOwned(userId, playerId));
    }

    /** 다른 유저도 볼 수 있는 프로필 — 랭킹에 오른(은퇴한) 선수만 */
    @Transactional(readOnly = true)
    public Summary publicSummary(Long playerId) {
        if (!rankingRepository.existsByPlayerId(playerId)) {
            throw new PlayerNotFoundException();
        }
        return summaryOf(playerRepository.findById(playerId).orElseThrow(PlayerNotFoundException::new));
    }

    private Summary summaryOf(Player player) {
        Long playerId = player.getId();
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));

        List<PlayerSeason> seasons = seasonRepository.findByPlayerIdOrderBySeasonYearAsc(playerId);
        List<SeasonRow> rows = seasons.stream()
                .map(s -> new SeasonRow(s, teams.get(s.getTeamId()), seasonService.batting(s), seasonService.pitching(s)))
                .toList();

        BattingLine mlbBatting = new BattingLine();
        PitchingLine mlbPitching = new PitchingLine();
        for (SeriesRecord record : seriesRecordRepository.findByPlayerIdOrderBySeasonYearAscSeriesNoAsc(playerId)) {
            if (!record.getLeagueLevel().isTop()) {
                continue;
            }
            SeriesSummary summary = seriesRecorder.readSummary(record);
            if (summary.batting() != null) mlbBatting.add(summary.batting());
            if (summary.pitching() != null) mlbPitching.add(summary.pitching());
        }
        int majorSeasons = (int) seasons.stream().filter(s -> s.getMlbSeriesPlayed() >= MAJOR_SEASON_SERIES).count();
        List<Award> awards = awardService.career(playerId);
        int awardPoints = awards.stream().mapToInt(Award::getPoints).sum();

        Map<Integer, PlayerSeason> seasonByYear = seasons.stream()
                .collect(Collectors.toMap(PlayerSeason::getSeasonYear, Function.identity()));
        List<Postseason> postseasons = postseasonRepository.findByPlayerIdOrderBySeasonYearAsc(playerId);
        Map<Integer, Long> championTeamByYear = championships(postseasons, seasonByYear);
        List<Integer> championshipYears = championTeamByYear.keySet().stream().sorted().toList();
        int score = hallOfFameScore(player.isPitcher(), mlbBatting, mlbPitching, awardPoints, majorSeasons,
                championshipYears.size(), LeagueRulesets.of(player).hallOfFameStatScale());

        SeasonSplits.Tables records = seasonSplits.career(playerId, championTeamByYear);
        Map<Integer, Integer> seriesPlayedByYear = seasons.stream()
                .collect(Collectors.toMap(PlayerSeason::getSeasonYear, PlayerSeason::getSeriesPlayed));
        SeasonMetrics.DetailTables detail = seasonMetrics.detail(records, player.getPosition(), player.isPitcher(),
                leagueWorldRepository.findByPlayerId(playerId).orElse(null), seriesPlayedByYear);

        return new Summary(player, rows, mlbBatting, mlbPitching, majorSeasons, awards, awardPoints,
                earningsService.careerTotal(playerId), seasonService.levelHistory(playerId),
                tradeService.career(playerId), score, records,
                postseasonRows(postseasons, seasonByYear, teams, championTeamByYear, LeagueRulesets.of(player).topLevel()),
                championshipYears, detail);
    }

    /**
     * 우승 연도 → 우승 구단. 메이저 소속으로 정규시즌을 마친 팀(시즌 중 트레이드면 마친 팀)이 우승해야 인정.
     * 시즌 기록의 구단·레벨은 정규시즌 마지막 상태이고, 포스트시즌은 그 구단으로 치른다.
     */
    public static Map<Integer, Long> championships(List<Postseason> postseasons,
                                                   Map<Integer, PlayerSeason> seasonByYear) {
        Map<Integer, Long> champions = new HashMap<>();
        for (Postseason p : postseasons) {
            PlayerSeason season = seasonByYear.get(p.getSeasonYear());
            if (p.isDone() && season != null && season.getLeagueLevel().isTop()
                    && season.getTeamId().equals(p.getChampionTeamId())) {
                champions.put(p.getSeasonYear(), p.getChampionTeamId());
            }
        }
        return champions;
    }

    /** 포스트시즌 기록표: 내 선수가 직접 뛴(메이저 소속·출전) 해만, 맨 아래 통산 */
    private List<SeasonSplits.Row> postseasonRows(List<Postseason> postseasons, Map<Integer, PlayerSeason> seasonByYear,
                                                  Map<Long, Team> teams, Map<Integer, Long> championTeamByYear,
                                                  LeagueLevel top) {
        List<SeasonSplits.Row> rows = new ArrayList<>();
        BattingLine totalBatting = new BattingLine();
        PitchingLine totalPitching = new PitchingLine();
        for (Postseason p : postseasons) {
            PlayerSeason season = seasonByYear.get(p.getSeasonYear());
            BattingLine batting = postseasonService.batting(p);
            PitchingLine pitching = postseasonService.pitching(p);
            if (season == null || !p.isUserPlays() || (batting.games == 0 && pitching.games == 0)) {
                continue;
            }
            rows.add(new SeasonSplits.Row(SeasonSplits.Kind.SPLIT, p.getSeasonYear(), top,
                    teams.get(season.getTeamId()), 1, batting, pitching, null,
                    championTeamByYear.containsKey(p.getSeasonYear())));
            totalBatting.add(batting);
            totalPitching.add(pitching);
        }
        if (!rows.isEmpty()) {
            rows.add(new SeasonSplits.Row(SeasonSplits.Kind.CAREER_TOTAL, null, top, null, 0,
                    totalBatting, totalPitching, null, false));
        }
        return rows;
    }

    static int hallOfFameScore(boolean pitcher, BattingLine batting, PitchingLine pitching, int awardPoints,
                               int majorSeasons, int championships) {
        return hallOfFameScore(pitcher, batting, pitching, awardPoints, majorSeasons, championships, 1.0);
    }

    /**
     * @param statScale 누적 기록 보정 — 시즌 경기 수가 적은 리그(KBO 144경기)는 같은 커리어라도 누적이 적으므로
     *                  162 / 시즌 경기 수 만큼 키운다 ({@code LeagueRuleset.hallOfFameStatScale})
     */
    static int hallOfFameScore(boolean pitcher, BattingLine batting, PitchingLine pitching, int awardPoints,
                               int majorSeasons, int championships, double statScale) {
        double score = awardPoints * 3 + majorSeasons * 2 + championships * CHAMPIONSHIP_POINTS;
        if (pitcher) {
            score += (pitching.wins * 2 + pitching.saves / 2.0 + pitching.strikeouts / 30.0) * statScale;
        } else {
            score += (batting.hits / 20.0 + batting.homeRuns / 5.0) * statScale;
        }
        return (int) Math.round(score);
    }
}
