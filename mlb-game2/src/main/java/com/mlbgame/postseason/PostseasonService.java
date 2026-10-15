package com.mlbgame.postseason;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlbgame.award.AwardService;
import com.mlbgame.award.AwardType;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.standings.StandingsService;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.league.world.TeamStrengthService;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.Position;
import com.mlbgame.postseason.Bracket.Series;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
import com.mlbgame.season.SeasonService.MlbSeason;
import com.mlbgame.season.SeriesRecorder;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.GameResult;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.game.TeamRatings;
import com.mlbgame.simulation.rules.PitcherUsage;
import com.mlbgame.simulation.series.SeriesInput;
import com.mlbgame.simulation.series.SeriesResult;
import com.mlbgame.simulation.series.SeriesSimulator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포스트시즌 진행. 정규시즌(54시리즈)과 마지막 훈련을 마친 뒤 시작한다.
 *
 * <ul>
 *   <li>시드: 54시리즈 종료 시점 순위표 (리그당 6팀)</li>
 *   <li>라운드마다 한 번 진행 — 내 소속 구단의 시리즈는 내 선수가 메이저 소속이면 경기 단위로 직접 뛰고,
 *       나머지 시리즈는 팀 전력으로 진행</li>
 *   <li>내 구단이 탈락했거나 진출하지 못했으면 남은 라운드를 한 번에 끝까지 진행</li>
 *   <li>챔피언십·월드시리즈를 이기고 내 선수 시리즈 기록이 기준을 넘으면 MVP</li>
 * </ul>
 * 포스트시즌 기록은 정규시즌 기록과 따로 보관하고, 훈련·연봉 적립은 없다.
 */
@Service
public class PostseasonService {

    static final int LCS_MARKER = 55;
    static final int WS_MARKER = 56;
    // 시리즈 MVP 기준 (팀이 이긴 경우)
    static final int MVP_MIN_PA = 12;
    static final double MVP_MIN_OPS = 0.900;
    static final int MVP_MIN_SP_OUTS = 30;
    static final double MVP_MAX_SP_ERA = 2.50;
    static final int MVP_MIN_SAVES = 2;

    private final PlayerService playerService;
    private final SeasonService seasonService;
    private final LeagueWorldService leagueWorldService;
    private final StandingsService standingsService;
    private final TeamStrengthService teamStrengthService;
    private final SeriesSimulator seriesSimulator;
    private final PostseasonRepository repository;
    private final AwardService awardService;
    private final ObjectMapper objectMapper;

    public PostseasonService(PlayerService playerService, SeasonService seasonService,
                             LeagueWorldService leagueWorldService, StandingsService standingsService,
                             TeamStrengthService teamStrengthService, SeriesSimulator seriesSimulator,
                             PostseasonRepository repository, AwardService awardService, ObjectMapper objectMapper) {
        this.playerService = playerService;
        this.seasonService = seasonService;
        this.leagueWorldService = leagueWorldService;
        this.standingsService = standingsService;
        this.teamStrengthService = teamStrengthService;
        this.seriesSimulator = seriesSimulator;
        this.repository = repository;
        this.awardService = awardService;
        this.objectMapper = objectMapper;
    }

    /** 포스트시즌 (없으면 시작). 진출하지 못했으면 시작과 동시에 끝까지 진행 */
    @Transactional
    public Postseason getOrStart(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        PlayerSeason season = seasonService.currentSeason(userId, playerId);
        if (!season.isCompleted() || season.isTrainingPending()) {
            throw new SeasonProgressException("정규시즌 54시리즈와 마지막 훈련을 마친 뒤 포스트시즌이 열립니다.");
        }
        return repository.findByPlayerIdAndSeasonYear(playerId, season.getSeasonYear())
                .orElseGet(() -> start(userId, player, season));
    }

    private Postseason start(Long userId, Player player, PlayerSeason season) {
        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());
        MlbSeason mlb = seasonService.mlbSeason(userId, player.getId());
        StandingsTable table = standingsService.standings(world, season.getSeasonYear(), PlayerSeason.SERIES_PER_SEASON,
                player.getTeam().getId(), mlb.teamResults());
        Bracket bracket = PostseasonEngine.initial(table);
        boolean qualified = bracket.seeds.stream().anyMatch(s -> s.teamId.equals(player.getTeam().getId()));
        boolean plays = qualified && season.getLeagueLevel() == LeagueLevel.MLB && !season.isSidelined();
        Postseason postseason = repository.save(new Postseason(player.getId(), season.getSeasonYear(), qualified, plays,
                write(bracket), season.getFatigue(), season.usage()));
        if (!qualified) {
            playRemaining(player, postseason, world, bracket);
        }
        return postseason;
    }

    @Transactional
    public Postseason playNextRound(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        Postseason postseason = getOrStart(userId, playerId);
        if (postseason.isDone()) {
            throw new SeasonProgressException("이번 포스트시즌은 끝났습니다.");
        }
        LeagueWorld world = leagueWorldService.getOrCreate(playerId);
        Bracket bracket = read(postseason.getBracketJson(), Bracket.class, Bracket::new);
        playRound(player, postseason, world, bracket);
        if (!postseason.isDone() && !bracket.alive(player.getTeam().getId())) {
            playRemaining(player, postseason, world, bracket);   // 탈락하면 남은 라운드는 한 번에
        }
        return postseason;
    }

    public Bracket bracket(Postseason postseason) {
        return read(postseason.getBracketJson(), Bracket.class, Bracket::new);
    }

    public BattingLine batting(Postseason postseason) {
        return read(postseason.getBattingJson(), BattingLine.class, BattingLine::new);
    }

    public PitchingLine pitching(Postseason postseason) {
        return read(postseason.getPitchingJson(), PitchingLine.class, PitchingLine::new);
    }

    // ------------------------------------------------------------ 진행

    private void playRemaining(Player player, Postseason postseason, LeagueWorld world, Bracket bracket) {
        while (!postseason.isDone()) {
            playRound(player, postseason, world, bracket);
        }
    }

    private void playRound(Player player, Postseason postseason, LeagueWorld world, Bracket bracket) {
        int round = postseason.getNextRound();
        Long myTeam = player.getTeam().getId();
        Map<Long, Double> overall = standingsService.overallRatings(world);
        Map<Long, TeamRatings> ratings = teamStrengthService.ratingsByTeam(world.getId(), LeagueLevel.MLB);

        BattingLine batting = batting(postseason);
        PitchingLine pitching = pitching(postseason);
        int fatigue = postseason.getFatigue();
        PitcherUsage usage = postseason.usage();
        boolean injured = postseason.isInjured();

        List<Series> seriesList = bracket.round(round);
        for (int i = 0; i < seriesList.size(); i++) {
            Series series = seriesList.get(i);
            if (series.involves(myTeam)) {
                series.userTeam = true;
            }
            if (series.involves(myTeam) && postseason.isUserPlays()) {
                UserSeries user = playUserSeries(player, series, ratings, fatigue, usage, injured);
                batting.add(user.batting);
                pitching.add(user.pitching);
                fatigue = user.fatigue;
                usage = user.usage;
                injured = user.injured;
                series.userSummary = summary(player, user);
                checkMvp(player, postseason.getSeasonYear(), round, series, myTeam, user);
            } else {
                SplittableRandom rng = new SplittableRandom(world.getSeed() * 41 + postseason.getSeasonYear() * 7L
                        + round * 101L + i);
                PostseasonEngine.simulate(series, overall.getOrDefault(series.highTeamId, 65.0),
                        overall.getOrDefault(series.lowTeamId, 65.0), rng);
            }
        }
        if (round < PostseasonRound.WORLD_SERIES.getNumber()) {
            PostseasonEngine.createNextRound(bracket, round);
        }
        postseason.updateUsage(usage);
        postseason.afterRound(write(bracket), write(batting), write(pitching), fatigue, injured);
        if (round == PostseasonRound.WORLD_SERIES.getNumber()) {
            postseason.finish(bracket.champion());
        }
    }

    private record UserSeries(BattingLine batting, PitchingLine pitching, int fatigue, PitcherUsage usage,
                              boolean injured) {
    }

    /** 내 팀 시리즈: 경기마다 1경기짜리 시리즈 입력으로 엔진을 돌린다 (피로·등판 상태는 경기 사이에 이어짐) */
    private UserSeries playUserSeries(Player player, Series series, Map<Long, TeamRatings> ratings,
                                      int fatigue, PitcherUsage usage, boolean injured) {
        PostseasonRound round = PostseasonRound.of(series.round);
        Long myTeam = player.getTeam().getId();
        boolean userHigh = myTeam.equals(series.highTeamId);
        Long opponent = userHigh ? series.lowTeamId : series.highTeamId;
        TeamRatings mine = ratings.getOrDefault(myTeam, TeamRatings.uniform(65));
        TeamRatings theirs = ratings.getOrDefault(opponent, TeamRatings.uniform(65));
        PlayerSnapshot snapshot = PlayerSnapshot.of(player);

        BattingLine batting = new BattingLine();
        PitchingLine pitching = new PitchingLine();
        int game = 0;
        while (series.highWins < round.winsNeeded() && series.lowWins < round.winsNeeded()) {
            boolean home = round.highSeedHome(game) == userHigh;
            SeriesResult result = seriesSimulator.simulate(new SeriesInput(SeriesRecorder.newSeed(), LeagueLevel.MLB,
                    home, mine, theirs, 1, snapshot, fatigue, usage, injured));
            GameResult g = result.games().get(0);
            if (g.isWin() == userHigh) series.highWins++;
            else series.lowWins++;
            series.scores.add(userHigh ? g.teamScore() + "-" + g.opponentScore() : g.opponentScore() + "-" + g.teamScore());
            if (result.batting() != null) batting.add(result.batting());
            if (result.pitching() != null) pitching.add(result.pitching());
            fatigue = result.endFatigue();
            usage = result.endUsage();
            injured = injured || result.injury() != null;
            game++;
        }
        series.winnerId = series.highWins > series.lowWins ? series.highTeamId : series.lowTeamId;
        return new UserSeries(batting, pitching, fatigue, usage, injured);
    }

    private void checkMvp(Player player, int year, int round, Series series, Long myTeam, UserSeries user) {
        boolean won = myTeam.equals(series.winnerId);
        if (!won || (round != PostseasonRound.CHAMPIONSHIP.getNumber() && round != PostseasonRound.WORLD_SERIES.getNumber())) {
            return;
        }
        if (!worthyOfMvp(player.getPosition(), user.batting, user.pitching)) {
            return;
        }
        if (round == PostseasonRound.CHAMPIONSHIP.getNumber()) {
            awardService.grantPostseason(player, year, LCS_MARKER, AwardType.LCS_MVP);
        } else {
            awardService.grantPostseason(player, year, WS_MARKER, AwardType.WORLD_SERIES_MVP);
        }
    }

    static boolean worthyOfMvp(Position position, BattingLine batting, PitchingLine pitching) {
        if (!position.isPitcher()) {
            return batting.plateAppearances >= MVP_MIN_PA && batting.getOps() >= MVP_MIN_OPS;
        }
        if (position == Position.SP) {
            return pitching.outs >= MVP_MIN_SP_OUTS && pitching.getEra() <= MVP_MAX_SP_ERA;
        }
        return pitching.saves >= MVP_MIN_SAVES && pitching.runs == 0;
    }

    private static String summary(Player player, UserSeries user) {
        if (player.isPitcher()) {
            return user.pitching.games == 0 ? "등판 없음" : user.pitching.games + "경기 " + user.pitching.summary();
        }
        return user.batting.plateAppearances == 0 ? "출전 없음" : user.batting.summary();
    }

    private <T> T read(String json, Class<T> type, Supplier<T> empty) {
        if (json == null || json.isBlank()) {
            return empty.get();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("포스트시즌 데이터를 읽을 수 없습니다.", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("포스트시즌 데이터 저장 실패", e);
        }
    }
}
