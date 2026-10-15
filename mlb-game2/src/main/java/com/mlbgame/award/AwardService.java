package com.mlbgame.award;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.stats.LeagueStatsService;
import com.mlbgame.league.stats.LeagueStatsService.PlayerLine;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.RosterRole;
import com.mlbgame.player.Player;
import com.mlbgame.player.Position;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.PlayerSeasonRepository;
import com.mlbgame.season.SeriesRecord;
import com.mlbgame.season.SeriesRecordRepository;
import com.mlbgame.season.SeriesRecorder;
import com.mlbgame.season.SeriesSummary;
import com.mlbgame.season.earnings.EarningType;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시상 판정과 지급. 시리즈가 끝날 때 {@link #evaluateAfterSeries} 를 호출한다.
 *
 * <ul>
 *   <li>9시리즈마다 월간 시상 (메이저: 이달의 선수·투수·루키·구원투수 / 마이너: 마이너리그 이달의 선수)</li>
 *   <li>27시리즈 후 올스타 (메이저 올스타 + 올스타전 MVP / 마이너리그 올스타)</li>
 *   <li>54시리즈 후 시즌 시상 (MVP·사이영·신인왕·실버슬러거·골드글러브·올해의 구원투수 / 마이너리그 올해의 선수)</li>
 * </ul>
 * 비교 대상: 메이저는 같은 리그(AL/NL) NPC 기록(통계 생성), 마이너는 레벨별 가상 유망주 30명.
 * 수상하면 스탯업 포인트 적립 + 수상 보너스를 수령 원장에 기록.
 */
@Service
public class AwardService {

    static final int SERIES_PER_MONTH = 9;
    static final int ALL_STAR_AFTER = 27;
    static final int SEASON_END = 54;
    static final int GAMES_PER_SERIES = 3;
    /** 이전 시즌까지 메이저 시리즈가 이보다 적으면 신인 */
    static final int ROOKIE_LIMIT_SERIES = 15;

    private final AwardRepository awardRepository;
    private final SeriesRecordRepository seriesRecordRepository;
    private final SeriesRecorder seriesRecorder;
    private final PlayerSeasonRepository seasonRepository;
    private final LeagueStatsService leagueStatsService;
    private final MinorLeaguePool minorLeaguePool;
    private final EarningsService earningsService;

    public AwardService(AwardRepository awardRepository, SeriesRecordRepository seriesRecordRepository,
                        SeriesRecorder seriesRecorder, PlayerSeasonRepository seasonRepository,
                        LeagueStatsService leagueStatsService, MinorLeaguePool minorLeaguePool,
                        EarningsService earningsService) {
        this.awardRepository = awardRepository;
        this.seriesRecordRepository = seriesRecordRepository;
        this.seriesRecorder = seriesRecorder;
        this.seasonRepository = seasonRepository;
        this.leagueStatsService = leagueStatsService;
        this.minorLeaguePool = minorLeaguePool;
        this.earningsService = earningsService;
    }

    /**
     * @param level 방금 끝난 시리즈의 소속 레벨 (승격·강등 반영 전)
     */
    @Transactional
    public List<Award> evaluateAfterSeries(Player player, PlayerSeason season, int seriesNo, LeagueLevel level,
                                           LeagueWorld world) {
        List<Award> granted = new ArrayList<>();
        if (seriesNo % SERIES_PER_MONTH == 0) {
            monthly(player, season, seriesNo, level, world, granted);
        }
        if (seriesNo == ALL_STAR_AFTER) {
            allStar(player, season, level, world, granted);
        }
        if (seriesNo == SEASON_END) {
            seasonAwards(player, season, world, granted);
        }
        return granted;
    }

    @Transactional(readOnly = true)
    public List<Award> awardsAfter(Long playerId, int year, int seriesNo) {
        return awardRepository.findByPlayerIdAndSeasonYearAndAfterSeries(playerId, year, seriesNo);
    }

    @Transactional(readOnly = true)
    public List<Award> career(Long playerId) {
        return awardRepository.findByPlayerIdOrderByIdAsc(playerId);
    }

    // ------------------------------------------------------------ 월간

    private void monthly(Player player, PlayerSeason season, int seriesNo, LeagueLevel level, LeagueWorld world,
                         List<Award> granted) {
        int month = seriesNo / SERIES_PER_MONTH;                    // 1~6
        Lines mine = userLines(player, season, seriesNo - SERIES_PER_MONTH + 1, seriesNo, r -> true);
        int teamGames = SERIES_PER_MONTH * GAMES_PER_SERIES;

        if (level != LeagueLevel.MLB) {
            long seed = poolSeed(world, season.getSeasonYear(), month, level);
            if (wins(player, mine, minorPool(seed, level, teamGames, player.getPosition()),
                    AwardRules.MONTH_MIN_PA, AwardRules.MONTH_MIN_SP_OUTS, AwardRules.MONTH_MIN_RP_OUTS)) {
                grant(player, season, seriesNo, AwardType.MINOR_PLAYER_OF_MONTH, granted);
            }
            return;
        }

        List<PlayerLine> npcs = leagueLines(player, world, season.getSeasonYear(),
                List.of(LeagueStatsService.FIRST_MONTH + month - 1));
        Position pos = player.getPosition();
        if (!pos.isPitcher() && mine.batting.plateAppearances >= AwardRules.MONTH_MIN_PA
                && AwardRules.beatsAll(AwardRules.hitter(mine.batting, mine.fielding),
                hitterScores(npcs, AwardRules.MONTH_MIN_PA, false))) {
            grant(player, season, seriesNo, AwardType.PLAYER_OF_MONTH, granted);
        }
        if (pos == Position.SP && mine.pitching.outs >= AwardRules.MONTH_MIN_SP_OUTS
                && AwardRules.beatsAll(AwardRules.starter(mine.pitching), starterScores(npcs, AwardRules.MONTH_MIN_SP_OUTS, false))) {
            grant(player, season, seriesNo, AwardType.PITCHER_OF_MONTH, granted);
        }
        if (pos == Position.CP && mine.pitching.outs >= AwardRules.MONTH_MIN_RP_OUTS
                && AwardRules.beatsAll(AwardRules.reliever(mine.pitching), relieverScores(npcs, AwardRules.MONTH_MIN_RP_OUTS, false))) {
            grant(player, season, seriesNo, AwardType.RELIEVER_OF_MONTH, granted);
        }
        if (isRookie(player, season) && userScore(pos, mine) > Double.NEGATIVE_INFINITY
                && qualifies(pos, mine, AwardRules.MONTH_MIN_PA, AwardRules.MONTH_MIN_SP_OUTS, AwardRules.MONTH_MIN_RP_OUTS)) {
            List<Double> rookies = new ArrayList<>();
            rookies.addAll(hitterScores(npcs, AwardRules.MONTH_MIN_PA, true));
            rookies.addAll(starterScores(npcs, AwardRules.MONTH_MIN_SP_OUTS, true));
            rookies.addAll(relieverScores(npcs, AwardRules.MONTH_MIN_RP_OUTS, true));
            if (AwardRules.beatsAll(userScore(pos, mine), rookies)) {
                grant(player, season, seriesNo, AwardType.ROOKIE_OF_MONTH, granted);
            }
        }
    }

    // ------------------------------------------------------------ 올스타

    private void allStar(Player player, PlayerSeason season, LeagueLevel level, LeagueWorld world, List<Award> granted) {
        Lines mine = userLines(player, season, 1, ALL_STAR_AFTER, r -> true);
        Position pos = player.getPosition();
        int firstHalfGames = ALL_STAR_AFTER * GAMES_PER_SERIES;

        if (level != LeagueLevel.MLB) {
            long seed = poolSeed(world, season.getSeasonYear(), 99, level);
            List<Double> pool = minorPool(seed, level, firstHalfGames, pos);
            if (qualifies(pos, mine, AwardRules.ALL_STAR_MIN_PA, AwardRules.ALL_STAR_MIN_SP_OUTS, AwardRules.ALL_STAR_MIN_RP_OUTS)
                    && AwardRules.rank(userScore(pos, mine), pool) <= AwardRules.MINOR_ALL_STAR_RANK) {
                grant(player, season, ALL_STAR_AFTER, AwardType.MINOR_ALL_STAR, granted);
            }
            return;
        }

        Lines mlb = userLines(player, season, 1, ALL_STAR_AFTER, r -> r.getLeagueLevel() == LeagueLevel.MLB);
        if (!qualifies(pos, mlb, AwardRules.ALL_STAR_MIN_PA, AwardRules.ALL_STAR_MIN_SP_OUTS, AwardRules.ALL_STAR_MIN_RP_OUTS)) {
            return;
        }
        List<PlayerLine> npcs = leagueLines(player, world, season.getSeasonYear(), LeagueStatsService.monthsThrough(3));
        boolean selected;
        if (!pos.isPitcher()) {
            List<Double> samePosition = npcs.stream()
                    .filter(l -> !l.isPitcher() && l.npc().getPosition() == pos
                            && l.batting().plateAppearances >= AwardRules.ALL_STAR_MIN_PA)
                    .map(l -> AwardRules.hitter(l.batting(), l.fielding()))
                    .toList();
            selected = AwardRules.rank(AwardRules.hitter(mlb.batting, mlb.fielding), samePosition)
                    <= AwardRules.ALL_STAR_HITTERS_PER_POSITION;
        } else if (pos == Position.SP) {
            selected = AwardRules.rank(AwardRules.starter(mlb.pitching),
                    starterScores(npcs, AwardRules.ALL_STAR_MIN_SP_OUTS, false)) <= AwardRules.ALL_STAR_STARTERS;
        } else {
            selected = AwardRules.rank(AwardRules.reliever(mlb.pitching),
                    relieverScores(npcs, AwardRules.ALL_STAR_MIN_RP_OUTS, false)) <= AwardRules.ALL_STAR_RELIEVERS;
        }
        if (selected) {
            grant(player, season, ALL_STAR_AFTER, AwardType.ALL_STAR, granted);
            SplittableRandom rng = new SplittableRandom(world.getSeed() * 13 + season.getSeasonYear() * 7L + player.getId());
            if (rng.nextDouble() < AwardRules.ALL_STAR_MVP_CHANCE) {
                grant(player, season, ALL_STAR_AFTER, AwardType.ALL_STAR_MVP, granted);
            }
        }
    }

    // ------------------------------------------------------------ 시즌

    private void seasonAwards(Player player, PlayerSeason season, LeagueWorld world, List<Award> granted) {
        Position pos = player.getPosition();
        Lines mlb = userLines(player, season, 1, SEASON_END, r -> r.getLeagueLevel() == LeagueLevel.MLB);
        int year = season.getSeasonYear();

        if (mlb.series > 0) {
            List<PlayerLine> npcs = leagueLines(player, world, year, LeagueStatsService.monthsThrough(6));
            if (!pos.isPitcher()) {
                double mine = AwardRules.hitter(mlb.batting, mlb.fielding);
                if (mlb.batting.plateAppearances >= AwardRules.SEASON_MIN_PA
                        && AwardRules.beatsAll(mine, hitterScores(npcs, AwardRules.SEASON_MIN_PA, false))) {
                    grant(player, season, SEASON_END, AwardType.MVP, granted);
                }
                if (mlb.batting.plateAppearances >= AwardRules.SEASON_MIN_PA) {
                    List<Double> samePosition = npcs.stream()
                            .filter(l -> !l.isPitcher() && l.npc().getPosition() == pos
                                    && l.batting().plateAppearances >= AwardRules.SEASON_MIN_PA)
                            .map(l -> AwardRules.hitter(l.batting(), null)).toList();
                    if (AwardRules.beatsAll(AwardRules.hitter(mlb.batting, null), samePosition)) {
                        grant(player, season, SEASON_END, AwardType.SILVER_SLUGGER, granted);
                    }
                }
                if (mlb.batting.games >= AwardRules.GOLD_GLOVE_MIN_GAMES) {
                    List<Double> fielders = npcs.stream()
                            .filter(l -> !l.isPitcher() && l.npc().getPosition() == pos
                                    && l.batting().games >= AwardRules.GOLD_GLOVE_MIN_GAMES)
                            .map(l -> l.fielding().getRunsSaved()).toList();
                    if (AwardRules.beatsAll(mlb.fielding.getRunsSaved(), fielders)) {
                        grant(player, season, SEASON_END, AwardType.GOLD_GLOVE, granted);
                    }
                }
            } else if (pos == Position.SP && mlb.pitching.outs >= AwardRules.SEASON_MIN_SP_OUTS
                    && AwardRules.beatsAll(AwardRules.starter(mlb.pitching), starterScores(npcs, AwardRules.SEASON_MIN_SP_OUTS, false))) {
                grant(player, season, SEASON_END, AwardType.CY_YOUNG, granted);
            } else if (pos == Position.CP && mlb.pitching.outs >= AwardRules.SEASON_MIN_RP_OUTS
                    && AwardRules.beatsAll(AwardRules.reliever(mlb.pitching), relieverScores(npcs, AwardRules.SEASON_MIN_RP_OUTS, false))) {
                grant(player, season, SEASON_END, AwardType.RELIEVER_OF_YEAR, granted);
            }

            if (isRookie(player, season)
                    && qualifies(pos, mlb, AwardRules.ROOKIE_MIN_PA, AwardRules.ROOKIE_MIN_SP_OUTS, AwardRules.SEASON_MIN_RP_OUTS / 3)) {
                List<Double> rookies = new ArrayList<>();
                rookies.addAll(hitterScores(npcs, AwardRules.ROOKIE_MIN_PA, true));
                rookies.addAll(starterScores(npcs, AwardRules.ROOKIE_MIN_SP_OUTS, true));
                rookies.addAll(relieverScores(npcs, AwardRules.SEASON_MIN_RP_OUTS / 3, true));
                if (AwardRules.beatsAll(userScore(pos, mlb), rookies)) {
                    grant(player, season, SEASON_END, AwardType.ROOKIE_OF_YEAR, granted);
                }
            }
        }

        // 마이너리그 올해의 선수: 시즌 절반 이상을 마이너에서 보낸 경우, 가장 오래 뛴 레벨 기준
        Lines minor = userLines(player, season, 1, SEASON_END, r -> r.getLeagueLevel() != LeagueLevel.MLB);
        if (minor.series >= SEASON_END / 2 && minor.mainLevel != null) {
            long seed = poolSeed(world, year, 100, minor.mainLevel);
            if (wins(player, minor, minorPool(seed, minor.mainLevel, minor.series * GAMES_PER_SERIES, pos),
                    AwardRules.SEASON_MIN_PA * minor.series / SEASON_END,
                    AwardRules.SEASON_MIN_SP_OUTS * minor.series / SEASON_END,
                    AwardRules.SEASON_MIN_RP_OUTS * minor.series / SEASON_END)) {
                grant(player, season, SEASON_END, AwardType.MINOR_PLAYER_OF_YEAR, granted);
            }
        }
    }

    // ------------------------------------------------------------ 포스트시즌

    /** 포스트시즌 MVP 등 정규시즌 밖의 상 (marker: 55 = 챔피언십시리즈 후, 56 = 월드시리즈 후) */
    @Transactional
    public Award grantPostseason(Player player, int year, int marker, AwardType type) {
        Award award = awardRepository.save(new Award(player.getId(), year, marker, type));
        player.addStatPoints(type.getPoints());
        earningsService.add(player.getId(), year, EarningType.AWARD_BONUS, type.getBonus());
        return award;
    }

    @Transactional(readOnly = true)
    public List<Award> season(Long playerId, int year) {
        return awardRepository.findByPlayerIdAndSeasonYearOrderByIdAsc(playerId, year);
    }

    // ------------------------------------------------------------ 공통

    private void grant(Player player, PlayerSeason season, int afterSeries, AwardType type, List<Award> granted) {
        Award award = awardRepository.save(new Award(player.getId(), season.getSeasonYear(), afterSeries, type));
        player.addStatPoints(type.getPoints());
        earningsService.add(player.getId(), season.getSeasonYear(), EarningType.AWARD_BONUS, type.getBonus());
        granted.add(award);
    }

    /** 내 선수 기간 기록 (시리즈 번호 from~to, 조건에 맞는 시리즈만) */
    private Lines userLines(Player player, PlayerSeason season, int from, int to, Predicate<SeriesRecord> filter) {
        Lines lines = new Lines();
        Map<LeagueLevel, Integer> byLevel = new EnumMap<>(LeagueLevel.class);
        for (SeriesRecord record : seriesRecordRepository
                .findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(player.getId(), season.getSeasonYear())) {
            if (record.getSeriesNo() < from || record.getSeriesNo() > to || !filter.test(record)) {
                continue;
            }
            SeriesSummary summary = seriesRecorder.readSummary(record);
            if (summary.batting() != null) lines.batting.add(summary.batting());
            if (summary.fielding() != null) lines.fielding.add(summary.fielding());
            if (summary.pitching() != null) lines.pitching.add(summary.pitching());
            lines.series++;
            byLevel.merge(record.getLeagueLevel(), 1, Integer::sum);
        }
        lines.mainLevel = byLevel.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return lines;
    }

    private static final class Lines {
        final BattingLine batting = new BattingLine();
        final FieldingLine fielding = new FieldingLine();
        final PitchingLine pitching = new PitchingLine();
        int series;
        LeagueLevel mainLevel;
    }

    private List<PlayerLine> leagueLines(Player player, LeagueWorld world, int year, List<Integer> months) {
        String league = player.getTeam().getLeague();
        return leagueStatsService.lines(world, year, months).stream()
                .filter(l -> l.team() != null && league.equals(l.team().getLeague()))
                .toList();
    }

    private static List<Double> hitterScores(List<PlayerLine> npcs, int minPa, boolean rookiesOnly) {
        return npcs.stream()
                .filter(l -> !l.isPitcher() && l.batting().plateAppearances >= minPa)
                .filter(l -> !rookiesOnly || l.npc().getServiceYears() == 0)
                .map(l -> AwardRules.hitter(l.batting(), l.fielding()))
                .toList();
    }

    private static List<Double> starterScores(List<PlayerLine> npcs, int minOuts, boolean rookiesOnly) {
        return npcs.stream()
                .filter(l -> l.npc().getRosterRole() == RosterRole.ROTATION && l.pitching().outs >= minOuts)
                .filter(l -> !rookiesOnly || l.npc().getServiceYears() == 0)
                .map(l -> AwardRules.starter(l.pitching()))
                .toList();
    }

    private static List<Double> relieverScores(List<PlayerLine> npcs, int minOuts, boolean rookiesOnly) {
        return npcs.stream()
                .filter(l -> (l.npc().getRosterRole() == RosterRole.CLOSER || l.npc().getRosterRole() == RosterRole.BULLPEN)
                        && l.pitching().outs >= minOuts)
                .filter(l -> !rookiesOnly || l.npc().getServiceYears() == 0)
                .map(l -> AwardRules.reliever(l.pitching()))
                .toList();
    }

    private List<Double> minorPool(long seed, LeagueLevel level, int teamGames, Position position) {
        if (!position.isPitcher()) {
            return minorLeaguePool.hitterScores(seed, level, teamGames);
        }
        return minorLeaguePool.pitcherScores(seed, level, teamGames, position == Position.SP);
    }

    private static boolean wins(Player player, Lines mine, List<Double> others, int minPa, int minSpOuts, int minRpOuts) {
        Position pos = player.getPosition();
        return qualifies(pos, mine, minPa, minSpOuts, minRpOuts) && AwardRules.beatsAll(userScore(pos, mine), others);
    }

    private static boolean qualifies(Position pos, Lines mine, int minPa, int minSpOuts, int minRpOuts) {
        if (!pos.isPitcher()) {
            return mine.batting.plateAppearances >= minPa;
        }
        return mine.pitching.outs >= (pos == Position.SP ? minSpOuts : minRpOuts);
    }

    private static double userScore(Position pos, Lines mine) {
        if (!pos.isPitcher()) {
            return AwardRules.hitter(mine.batting, mine.fielding);
        }
        return pos == Position.SP ? AwardRules.starter(mine.pitching) : AwardRules.reliever(mine.pitching);
    }

    /** 이전 시즌까지 메이저 시리즈가 15개 미만이면 신인 */
    private boolean isRookie(Player player, PlayerSeason season) {
        int before = seasonRepository.findByPlayerIdOrderBySeasonYearAsc(player.getId()).stream()
                .filter(s -> s.getSeasonYear() < season.getSeasonYear())
                .mapToInt(PlayerSeason::getMlbSeriesPlayed)
                .sum();
        return before < ROOKIE_LIMIT_SERIES;
    }

    private static long poolSeed(LeagueWorld world, int year, int month, LeagueLevel level) {
        return world.getSeed() * 101 + year * 37L + month * 11L + level.ordinal();
    }
}
