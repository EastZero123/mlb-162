package com.mlbgame.career;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.award.Award;
import com.mlbgame.combine.CombineSummary;
import com.mlbgame.combine.CombineSummaryRepository;
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
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.PlayerSeasonRepository;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
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
 *   타자 = 메이저 안타/20 + 홈런/5 + 수상 포인트 × 3 + 메이저 주요 시즌(27시리즈 이상) × 2
 *   투수 = 메이저 승 × 2 + 세이브/2 + 탈삼진/30 + 수상 포인트 × 3 + 메이저 주요 시즌 × 2
 *   150 이상 "명예의 전당 헌액" / 100 이상 "명예의 전당 후보"
 * </pre>
 */
@Service
public class CareerService {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    public static final int HALL_OF_FAME = 150;
    public static final int HALL_OF_FAME_CANDIDATE = 100;
    static final int MAJOR_SEASON_SERIES = 27;

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
                         UserRepository userRepository, PlayerRepository playerRepository) {
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
            throw new SeasonProgressException("이미 은퇴한 선수입니다.");
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

    public record Summary(Player player, List<SeasonRow> seasons, BattingLine mlbBatting, PitchingLine mlbPitching,
                          int majorSeasons, List<Award> awards, int awardPoints, long careerEarnings,
                          List<LevelChange> levelHistory, List<TradeEvent> trades, int hallOfFameScore) {

        public String getHallOfFameLabel() {
            if (hallOfFameScore >= HALL_OF_FAME) return "명예의 전당 헌액";
            if (hallOfFameScore >= HALL_OF_FAME_CANDIDATE) return "명예의 전당 후보";
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
            if (record.getLeagueLevel() != LeagueLevel.MLB) {
                continue;
            }
            SeriesSummary summary = seriesRecorder.readSummary(record);
            if (summary.batting() != null) mlbBatting.add(summary.batting());
            if (summary.pitching() != null) mlbPitching.add(summary.pitching());
        }
        int majorSeasons = (int) seasons.stream().filter(s -> s.getMlbSeriesPlayed() >= MAJOR_SEASON_SERIES).count();
        List<Award> awards = awardService.career(playerId);
        int awardPoints = awards.stream().mapToInt(Award::getPoints).sum();
        int score = hallOfFameScore(player.isPitcher(), mlbBatting, mlbPitching, awardPoints, majorSeasons);

        return new Summary(player, rows, mlbBatting, mlbPitching, majorSeasons, awards, awardPoints,
                earningsService.careerTotal(playerId), seasonService.levelHistory(playerId),
                tradeService.career(playerId), score);
    }

    static int hallOfFameScore(boolean pitcher, BattingLine batting, PitchingLine pitching, int awardPoints,
                               int majorSeasons) {
        double score = awardPoints * 3 + majorSeasons * 2;
        if (pitcher) {
            score += pitching.wins * 2 + pitching.saves / 2.0 + pitching.strikeouts / 30.0;
        } else {
            score += batting.hits / 20.0 + batting.homeRuns / 5.0;
        }
        return (int) Math.round(score);
    }
}
