package com.mlbgame.award;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.mode.LeagueRuleset;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.league.stats.Leaderboard;
import com.mlbgame.league.stats.Leaderboard.Candidate;
import com.mlbgame.league.stats.Leaderboard.Category;
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
import java.util.LinkedHashMap;
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
        LeagueRuleset rules = LeagueRulesets.of(player);
        if (seriesNo % rules.seriesPerMonth() == 0) {
            monthly(player, season, seriesNo, level, world, granted);
        }
        if (seriesNo == rules.allStarAfterSeries()) {
            allStar(player, season, level, world, granted);
        }
        if (seriesNo == rules.seriesPerSeason()) {
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
        LeagueRuleset rules = LeagueRulesets.of(player);
        AwardRules.Scoring scoring = rules.awards().scoring();
        int month = seriesNo / rules.seriesPerMonth();                    // 1~6
        Lines mine = userLines(player, season, seriesNo - rules.seriesPerMonth() + 1, seriesNo, r -> true);
        int teamGames = rules.gamesInMonth(month);

        if (!level.isTop()) {
            long seed = poolSeed(world, season.getSeasonYear(), month, level);
            if (wins(scoring, player, mine, minorPool(seed, level, teamGames, player.getPosition()),
                    AwardRules.MONTH_MIN_PA, AwardRules.MONTH_MIN_SP_OUTS, AwardRules.MONTH_MIN_RP_OUTS)) {
                grant(player, season, seriesNo, rules.awards().minorPlayerOfMonth(), granted);
            }
            return;
        }

        List<PlayerLine> npcs = leagueLines(player, world, season.getSeasonYear(),
                List.of(LeagueStatsService.FIRST_MONTH + month - 1));
        Position pos = player.getPosition();
        if (!pos.isPitcher() && mine.batting.plateAppearances >= AwardRules.MONTH_MIN_PA
                && AwardRules.beatsAll(scoring.hitter(mine.batting, mine.fielding),
                hitterScores(scoring, npcs, AwardRules.MONTH_MIN_PA, false))) {
            grant(player, season, seriesNo, rules.awards().playerOfMonth(), granted);
        }
        if (pos == Position.SP && mine.pitching.outs >= AwardRules.MONTH_MIN_SP_OUTS
                && AwardRules.beatsAll(scoring.starter(mine.pitching), starterScores(scoring, npcs, AwardRules.MONTH_MIN_SP_OUTS, false))) {
            grant(player, season, seriesNo, rules.awards().pitcherOfMonth(), granted);
        }
        if (pos == Position.CP && mine.pitching.outs >= AwardRules.MONTH_MIN_RP_OUTS
                && AwardRules.beatsAll(scoring.reliever(mine.pitching), relieverScores(scoring, npcs, AwardRules.MONTH_MIN_RP_OUTS, false))) {
            grant(player, season, seriesNo, rules.awards().relieverOfMonth(), granted);
        }
        if (isRookie(player, season) && userScore(scoring, pos, mine) > Double.NEGATIVE_INFINITY
                && qualifies(pos, mine, AwardRules.MONTH_MIN_PA, AwardRules.MONTH_MIN_SP_OUTS, AwardRules.MONTH_MIN_RP_OUTS)) {
            List<Double> rookies = new ArrayList<>();
            rookies.addAll(hitterScores(scoring, npcs, AwardRules.MONTH_MIN_PA, true));
            rookies.addAll(starterScores(scoring, npcs, AwardRules.MONTH_MIN_SP_OUTS, true));
            rookies.addAll(relieverScores(scoring, npcs, AwardRules.MONTH_MIN_RP_OUTS, true));
            if (AwardRules.beatsAll(userScore(scoring, pos, mine), rookies)) {
                grant(player, season, seriesNo, rules.awards().rookieOfMonth(), granted);
            }
        }
    }

    // ------------------------------------------------------------ 올스타

    private void allStar(Player player, PlayerSeason season, LeagueLevel level, LeagueWorld world, List<Award> granted) {
        LeagueRuleset rules = LeagueRulesets.of(player);
        AwardRules.Scoring scoring = rules.awards().scoring();
        Lines mine = userLines(player, season, 1, rules.allStarAfterSeries(), r -> true);
        Position pos = player.getPosition();
        int firstHalfGames = rules.gamesBetween(1, rules.allStarAfterSeries());

        if (!level.isTop()) {
            long seed = poolSeed(world, season.getSeasonYear(), 99, level);
            List<Double> pool = minorPool(seed, level, firstHalfGames, pos);
            if (qualifies(pos, mine, AwardRules.ALL_STAR_MIN_PA, AwardRules.ALL_STAR_MIN_SP_OUTS, AwardRules.ALL_STAR_MIN_RP_OUTS)
                    && AwardRules.rank(userScore(scoring, pos, mine), pool) <= AwardRules.MINOR_ALL_STAR_RANK) {
                grant(player, season, rules.allStarAfterSeries(), rules.awards().minorAllStar(), granted);
            }
            return;
        }

        Lines mlb = userLines(player, season, 1, rules.allStarAfterSeries(), r -> r.getLeagueLevel().isTop());
        if (!qualifies(pos, mlb, AwardRules.ALL_STAR_MIN_PA, AwardRules.ALL_STAR_MIN_SP_OUTS, AwardRules.ALL_STAR_MIN_RP_OUTS)) {
            return;
        }
        List<PlayerLine> npcs = leagueLines(player, world, season.getSeasonYear(), LeagueStatsService.monthsThrough(3));
        boolean selected;
        if (!pos.isPitcher()) {
            List<Double> samePosition = npcs.stream()
                    .filter(l -> !l.isPitcher() && l.npc().getPosition() == pos
                            && l.batting().plateAppearances >= AwardRules.ALL_STAR_MIN_PA)
                    .map(l -> scoring.hitter(l.batting(), l.fielding()))
                    .toList();
            selected = AwardRules.rank(scoring.hitter(mlb.batting, mlb.fielding), samePosition)
                    <= AwardRules.ALL_STAR_HITTERS_PER_POSITION;
        } else if (pos == Position.SP) {
            selected = AwardRules.rank(scoring.starter(mlb.pitching),
                    starterScores(scoring, npcs, AwardRules.ALL_STAR_MIN_SP_OUTS, false)) <= AwardRules.ALL_STAR_STARTERS;
        } else {
            selected = AwardRules.rank(scoring.reliever(mlb.pitching),
                    relieverScores(scoring, npcs, AwardRules.ALL_STAR_MIN_RP_OUTS, false)) <= AwardRules.ALL_STAR_RELIEVERS;
        }
        if (selected) {
            grant(player, season, rules.allStarAfterSeries(), rules.awards().allStar(), granted);
            SplittableRandom rng = new SplittableRandom(world.getSeed() * 13 + season.getSeasonYear() * 7L + player.getId());
            if (rng.nextDouble() < AwardRules.ALL_STAR_MVP_CHANCE) {
                grant(player, season, rules.allStarAfterSeries(), rules.awards().allStarMvp(), granted);
            }
        }
    }

    // ------------------------------------------------------------ 시즌

    private void seasonAwards(Player player, PlayerSeason season, LeagueWorld world, List<Award> granted) {
        LeagueRuleset rules = LeagueRulesets.of(player);
        AwardRules.Scoring scoring = rules.awards().scoring();
        Position pos = player.getPosition();
        Lines mlb = userLines(player, season, 1, rules.seriesPerSeason(), r -> r.getLeagueLevel().isTop());
        int year = season.getSeasonYear();

        if (mlb.series > 0) {
            List<PlayerLine> npcs = leagueLines(player, world, year, LeagueStatsService.monthsThrough(6));
            if (!pos.isPitcher()) {
                double mine = scoring.hitter(mlb.batting, mlb.fielding);
                if (mlb.batting.plateAppearances >= AwardRules.SEASON_MIN_PA
                        && AwardRules.beatsAll(mine, hitterScores(scoring, npcs, AwardRules.SEASON_MIN_PA, false))) {
                    grant(player, season, rules.seriesPerSeason(), rules.awards().mvp(), granted);
                }
                if (mlb.batting.plateAppearances >= AwardRules.SEASON_MIN_PA) {
                    List<Double> samePosition = npcs.stream()
                            .filter(l -> !l.isPitcher() && l.npc().getPosition() == pos
                                    && l.batting().plateAppearances >= AwardRules.SEASON_MIN_PA)
                            .map(l -> scoring.hitter(l.batting(), null)).toList();
                    if (AwardRules.beatsAll(scoring.hitter(mlb.batting, null), samePosition)) {
                        grant(player, season, rules.seriesPerSeason(), rules.awards().bestHitterAtPosition(), granted);
                    }
                }
                if (mlb.batting.games >= AwardRules.GOLD_GLOVE_MIN_GAMES) {
                    List<Double> fielders = npcs.stream()
                            .filter(l -> !l.isPitcher() && l.npc().getPosition() == pos
                                    && l.batting().games >= AwardRules.GOLD_GLOVE_MIN_GAMES)
                            .map(l -> l.fielding().getRunsSaved()).toList();
                    if (AwardRules.beatsAll(mlb.fielding.getRunsSaved(), fielders)) {
                        grant(player, season, rules.seriesPerSeason(), rules.awards().bestDefender(), granted);
                    }
                }
            } else if (pos == Position.SP && mlb.pitching.outs >= AwardRules.SEASON_MIN_SP_OUTS
                    && AwardRules.beatsAll(scoring.starter(mlb.pitching), starterScores(scoring, npcs, AwardRules.SEASON_MIN_SP_OUTS, false))) {
                grant(player, season, rules.seriesPerSeason(), rules.awards().bestStarter(), granted);
                if (rules.awards().pitcherMvp() && mlb.pitching.wins >= AwardRules.PITCHER_MVP_MIN_WINS
                        && mlb.pitching.getEra() <= AwardRules.PITCHER_MVP_MAX_ERA) {
                    grant(player, season, rules.seriesPerSeason(), rules.awards().mvp(), granted);
                }
            } else if (pos == Position.CP && mlb.pitching.outs >= AwardRules.SEASON_MIN_RP_OUTS
                    && AwardRules.beatsAll(scoring.reliever(mlb.pitching), relieverScores(scoring, npcs, AwardRules.SEASON_MIN_RP_OUTS, false))) {
                grant(player, season, rules.seriesPerSeason(), rules.awards().bestReliever(), granted);
            }

            if (isRookie(player, season)
                    && qualifies(pos, mlb, AwardRules.ROOKIE_MIN_PA, AwardRules.ROOKIE_MIN_SP_OUTS, AwardRules.SEASON_MIN_RP_OUTS / 3)) {
                List<Double> rookies = new ArrayList<>();
                rookies.addAll(hitterScores(scoring, npcs, AwardRules.ROOKIE_MIN_PA, true));
                rookies.addAll(starterScores(scoring, npcs, AwardRules.ROOKIE_MIN_SP_OUTS, true));
                rookies.addAll(relieverScores(scoring, npcs, AwardRules.SEASON_MIN_RP_OUTS / 3, true));
                if (AwardRules.beatsAll(userScore(scoring, pos, mlb), rookies)) {
                    grant(player, season, rules.seriesPerSeason(), rules.awards().rookieOfYear(), granted);
                }
            }
            if (rules.awards().titles()) {
                titles(player, season, rules, mlb, npcs, granted);
            }
        }

        // 마이너리그 올해의 선수: 시즌 절반 이상을 마이너에서 보낸 경우, 가장 오래 뛴 레벨 기준
        Lines minor = userLines(player, season, 1, rules.seriesPerSeason(), r -> !r.getLeagueLevel().isTop());
        if (minor.series >= rules.seriesPerSeason() / 2 && minor.mainLevel != null) {
            long seed = poolSeed(world, year, 100, minor.mainLevel);
            if (wins(scoring, player, minor, minorPool(seed, minor.mainLevel, (int) Math.round(minor.series * (double) rules.seasonGames() / rules.seriesPerSeason()), pos),
                    AwardRules.SEASON_MIN_PA * minor.series / rules.seriesPerSeason(),
                    AwardRules.SEASON_MIN_SP_OUTS * minor.series / rules.seriesPerSeason(),
                    AwardRules.SEASON_MIN_RP_OUTS * minor.series / rules.seriesPerSeason())) {
                grant(player, season, rules.seriesPerSeason(), rules.awards().minorPlayerOfYear(), granted);
            }
        }
    }

    /** KBO 개인 타이틀: 리더보드 기준 단독 1위 (비율 기록은 규정 타석·이닝, 동률이면 NPC 우선) */
    static final Map<Category, AwardType> TITLES = new LinkedHashMap<>();

    static {
        TITLES.put(Category.AVG, AwardType.KBO_TITLE_AVG);
        TITLES.put(Category.HR, AwardType.KBO_TITLE_HR);
        TITLES.put(Category.RBI, AwardType.KBO_TITLE_RBI);
        TITLES.put(Category.SB, AwardType.KBO_TITLE_SB);
        TITLES.put(Category.WINS, AwardType.KBO_TITLE_WINS);
        TITLES.put(Category.ERA, AwardType.KBO_TITLE_ERA);
        TITLES.put(Category.STRIKEOUTS, AwardType.KBO_TITLE_SO);
        TITLES.put(Category.SAVES, AwardType.KBO_TITLE_SAVES);
    }

    private void titles(Player player, PlayerSeason season, LeagueRuleset rules, Lines top, List<PlayerLine> npcs,
                        List<Award> granted) {
        List<Candidate> candidates = new ArrayList<>();
        for (PlayerLine line : npcs) {
            candidates.add(new Candidate(line.npc().getName(), "", line.isPitcher() ? null : line.batting(),
                    line.isPitcher() ? line.pitching() : null, false));
        }
        candidates.add(new Candidate(player.getName(), "", player.isPitcher() ? null : top.batting,
                player.isPitcher() ? top.pitching : null, true));
        for (Map.Entry<Category, AwardType> title : TITLES.entrySet()) {
            List<Leaderboard.Entry> leaders = Leaderboard.top(candidates, title.getKey(), rules.seasonGames());
            if (!leaders.isEmpty() && leaders.get(0).candidate().me()) {
                grant(player, season, rules.seriesPerSeason(), title.getValue(), granted);
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

    /** type 이 null 이면 그 모드에는 없는 상이라 주지 않는다 ({@link AwardCatalog}) */
    private void grant(Player player, PlayerSeason season, int afterSeries, AwardType type, List<Award> granted) {
        if (type == null) {
            return;
        }
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

    private static List<Double> hitterScores(AwardRules.Scoring scoring, List<PlayerLine> npcs, int minPa, boolean rookiesOnly) {
        return npcs.stream()
                .filter(l -> !l.isPitcher() && l.batting().plateAppearances >= minPa)
                .filter(l -> !rookiesOnly || l.npc().getServiceYears() == 0)
                .map(l -> scoring.hitter(l.batting(), l.fielding()))
                .toList();
    }

    private static List<Double> starterScores(AwardRules.Scoring scoring, List<PlayerLine> npcs, int minOuts, boolean rookiesOnly) {
        return npcs.stream()
                .filter(l -> l.npc().getRosterRole() == RosterRole.ROTATION && l.pitching().outs >= minOuts)
                .filter(l -> !rookiesOnly || l.npc().getServiceYears() == 0)
                .map(l -> scoring.starter(l.pitching()))
                .toList();
    }

    private static List<Double> relieverScores(AwardRules.Scoring scoring, List<PlayerLine> npcs, int minOuts, boolean rookiesOnly) {
        return npcs.stream()
                .filter(l -> (l.npc().getRosterRole() == RosterRole.CLOSER || l.npc().getRosterRole() == RosterRole.BULLPEN)
                        && l.pitching().outs >= minOuts)
                .filter(l -> !rookiesOnly || l.npc().getServiceYears() == 0)
                .map(l -> scoring.reliever(l.pitching()))
                .toList();
    }

    private List<Double> minorPool(long seed, LeagueLevel level, int teamGames, Position position) {
        if (!position.isPitcher()) {
            return minorLeaguePool.hitterScores(seed, level, teamGames);
        }
        return minorLeaguePool.pitcherScores(seed, level, teamGames, position == Position.SP);
    }

    private static boolean wins(AwardRules.Scoring scoring, Player player, Lines mine, List<Double> others, int minPa, int minSpOuts, int minRpOuts) {
        Position pos = player.getPosition();
        return qualifies(pos, mine, minPa, minSpOuts, minRpOuts) && AwardRules.beatsAll(userScore(scoring, pos, mine), others);
    }

    private static boolean qualifies(Position pos, Lines mine, int minPa, int minSpOuts, int minRpOuts) {
        if (!pos.isPitcher()) {
            return mine.batting.plateAppearances >= minPa;
        }
        return mine.pitching.outs >= (pos == Position.SP ? minSpOuts : minRpOuts);
    }

    private static double userScore(AwardRules.Scoring scoring, Position pos, Lines mine) {
        if (!pos.isPitcher()) {
            return scoring.hitter(mine.batting, mine.fielding);
        }
        return pos == Position.SP ? scoring.starter(mine.pitching) : scoring.reliever(mine.pitching);
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
