package com.mlbgame.postseason;

import com.mlbgame.league.standings.StandingsGenerator;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.league.standings.StandingsTable.PlayoffSeed;
import com.mlbgame.postseason.Bracket.Seed;
import com.mlbgame.postseason.Bracket.Series;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 포스트시즌 대진 생성과 NPC 시리즈 진행 (순수 로직).
 *
 * <pre>
 * 와일드카드  리그별 3 vs 6, 4 vs 5 (3전 2선승, 1·2번 시드 부전승)
 * 디비전      1 vs (4/5 승자), 2 vs (3/6 승자) (5전 3선승)
 * 챔피언십    디비전 승자끼리 (7전 4선승)
 * 월드시리즈  AL 우승 vs NL 우승 (7전 4선승) — 시드 번호가 낮은 쪽이 상위, 같으면 AL
 * 경기 승률   StandingsGenerator 와 같은 공식 + 홈 팀 전력 +2
 * </pre>
 */
public final class PostseasonEngine {

    static final double HOME_BONUS = 2.0;

    private PostseasonEngine() {
    }

    public static Bracket initial(StandingsTable table) {
        Bracket bracket = new Bracket();
        for (String league : StandingsTable.LEAGUES) {
            for (PlayoffSeed s : table.playoffSeeds(league)) {
                bracket.seeds.add(new Seed(league, s.seed(), s.row().team().getId(), s.divisionWinner()));
            }
            addSeries(bracket, PostseasonRound.WILD_CARD, league, bracket.seed(league, 3), bracket.seed(league, 6));
            addSeries(bracket, PostseasonRound.WILD_CARD, league, bracket.seed(league, 4), bracket.seed(league, 5));
        }
        return bracket;
    }

    /** completedRound 가 모두 끝난 뒤 다음 라운드 대진을 만든다 */
    public static void createNextRound(Bracket bracket, int completedRound) {
        switch (PostseasonRound.of(completedRound)) {
            case WILD_CARD -> {
                for (String league : StandingsTable.LEAGUES) {
                    Seed winner45 = winnerSeed(bracket, completedRound, league, 4);
                    Seed winner36 = winnerSeed(bracket, completedRound, league, 3);
                    addSeries(bracket, PostseasonRound.DIVISION, league, bracket.seed(league, 1), winner45);
                    addSeries(bracket, PostseasonRound.DIVISION, league, bracket.seed(league, 2), winner36);
                }
            }
            case DIVISION -> {
                for (String league : StandingsTable.LEAGUES) {
                    List<Seed> winners = bracket.round(completedRound).stream()
                            .filter(s -> s.league.equals(league))
                            .map(s -> seedOf(bracket, s.winnerId))
                            .sorted((a, b) -> Integer.compare(a.seed, b.seed))
                            .toList();
                    addSeries(bracket, PostseasonRound.CHAMPIONSHIP, league, winners.get(0), winners.get(1));
                }
            }
            case CHAMPIONSHIP -> {
                Seed al = seedOf(bracket, leagueChampion(bracket, "AL"));
                Seed nl = seedOf(bracket, leagueChampion(bracket, "NL"));
                boolean alHigh = al.seed <= nl.seed;
                addSeries(bracket, PostseasonRound.WORLD_SERIES, "WS", alHigh ? al : nl, alHigh ? nl : al);
            }
            case WORLD_SERIES -> {
            }
        }
    }

    /** NPC 시리즈: 한쪽이 필요한 승수에 도달할 때까지 경기별 승률로 진행 */
    public static void simulate(Series series, double highRating, double lowRating, RandomGenerator rng) {
        simulate(series, PostseasonRound.of(series.round), highRating, lowRating, rng);
    }

    /** 라운드 방식을 직접 받는 버전 (모드별 {@link PostseasonFormat}) */
    public static void simulate(Series series, RoundSpec round, double highRating, double lowRating,
                                RandomGenerator rng) {
        int game = 0;
        while (series.highWins < round.winsNeeded() && series.lowWins < round.winsNeeded()) {
            boolean highHome = round.highSeedHome(game);
            double p = StandingsGenerator.winProbability(
                    highRating + (highHome ? HOME_BONUS : 0), lowRating + (highHome ? 0 : HOME_BONUS));
            boolean highWins = rng.nextDouble() < p;
            int winnerRuns = 3 + rng.nextInt(6);
            int loserRuns = rng.nextInt(winnerRuns);
            if (highWins) {
                series.highWins++;
                series.scores.add(winnerRuns + "-" + loserRuns);
            } else {
                series.lowWins++;
                series.scores.add(loserRuns + "-" + winnerRuns);
            }
            game++;
        }
        series.winnerId = series.highWins > series.lowWins ? series.highTeamId : series.lowTeamId;
    }

    public static Long leagueChampion(Bracket bracket, String league) {
        return bracket.round(PostseasonRound.CHAMPIONSHIP.getNumber()).stream()
                .filter(s -> s.league.equals(league) && s.finished())
                .map(s -> s.winnerId).findFirst().orElse(null);
    }

    private static void addSeries(Bracket bracket, PostseasonRound round, String league, Seed high, Seed low) {
        bracket.series.add(new Series(round.getNumber(), league, high, low));
    }

    /** 와일드카드에서 highSeed 번 시드가 상위였던 시리즈의 승자 */
    private static Seed winnerSeed(Bracket bracket, int round, String league, int highSeed) {
        Series series = bracket.round(round).stream()
                .filter(s -> s.league.equals(league) && s.highSeed == highSeed)
                .findFirst().orElseThrow();
        return seedOf(bracket, series.winnerId);
    }

    private static Seed seedOf(Bracket bracket, Long teamId) {
        return bracket.seeds.stream().filter(s -> s.teamId.equals(teamId)).findFirst().orElseThrow();
    }
}
