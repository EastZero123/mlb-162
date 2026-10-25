package com.mlbgame.postseason;

import com.mlbgame.award.AwardType;
import com.mlbgame.common.Messages;
import com.mlbgame.league.standings.StandingsTable;
import com.mlbgame.postseason.Bracket.Seed;
import com.mlbgame.postseason.Bracket.Series;
import java.util.List;

/**
 * KBO 포스트시즌: 정규시즌 1~5위 계단식 (docs/kbo-mode.md 3.4).
 *
 * <pre>
 * 와일드카드 결정전  4위 vs 5위 — 4위가 1승 안고 시작, 최대 2경기 모두 4위 홈 (무승부는 다루지 않음)
 * 준플레이오프      3위 vs WC 승자 (5전 3선승, 2-2-1)
 * 플레이오프        2위 vs 준PO 승자 (5전 3선승, 2-2-1)
 * 한국시리즈        1위 vs PO 승자 (7전 4선승, 2-3-2)
 * </pre>
 * 라운드 수가 MLB 와 같은 4라 진행 흐름·저장 형식(postseasons, Bracket)은 그대로 쓴다.
 */
public final class KboPostseasonFormat implements PostseasonFormat {

    public static final KboPostseasonFormat INSTANCE = new KboPostseasonFormat();
    static final String LEAGUE = "KBO";
    static final int TEAMS = 5;

    public enum Round implements RoundSpec {
        WILD_CARD(1, "와일드카드 결정전", 2, "HH", 1),
        SEMI_PLAYOFF(2, "준플레이오프", 5, "HHAAH", 0),
        PLAYOFF(3, "플레이오프", 5, "HHAAH", 0),
        KOREAN_SERIES(4, "한국시리즈", 7, "HHAAAHH", 0);

        private final int number;
        private final String label;
        private final int maxGames;
        private final String homePattern;
        private final int highSeedStartWins;

        Round(int number, String label, int maxGames, String homePattern, int highSeedStartWins) {
            this.number = number;
            this.label = label;
            this.maxGames = maxGames;
            this.homePattern = homePattern;
            this.highSeedStartWins = highSeedStartWins;
        }

        @Override
        public int getNumber() {
            return number;
        }

        @Override
        public String getLabel() {
            return Messages.text("KboRound." + name(), label);
        }

        @Override
        public int getMaxGames() {
            return maxGames;
        }

        @Override
        public boolean highSeedHome(int gameIndex) {
            return homePattern.charAt(Math.min(gameIndex, homePattern.length() - 1)) == 'H';
        }

        @Override
        public int highSeedStartWins() {
            return highSeedStartWins;
        }
    }

    private KboPostseasonFormat() {
    }

    @Override
    public List<Round> rounds() {
        return List.of(Round.values());
    }

    /** 순위표 상위 5팀 (승률순, 같으면 구단 id 순 — {@code StandingsGenerator.table}) */
    @Override
    public Bracket initial(StandingsTable table) {
        Bracket bracket = new Bracket();
        List<StandingsTable.Row> rows = table.rows();
        for (int i = 0; i < Math.min(TEAMS, rows.size()); i++) {
            bracket.seeds.add(new Seed(LEAGUE, i + 1, rows.get(i).team().getId(), false));
        }
        addSeries(bracket, Round.WILD_CARD, bracket.seed(LEAGUE, 4), bracket.seed(LEAGUE, 5));
        return bracket;
    }

    /** 이긴 팀이 한 단계 위 순위 팀을 만난다 */
    @Override
    public void createNextRound(Bracket bracket, int completedRound) {
        Series finished = bracket.round(completedRound).get(0);
        Seed winner = bracket.seeds.stream().filter(s -> s.teamId.equals(finished.winnerId)).findFirst().orElseThrow();
        Round next = Round.values()[completedRound];             // completedRound 는 1부터 → 다음 라운드
        int waitingSeed = 3 - (completedRound - 1);             // 준PO 3위, PO 2위, KS 1위
        addSeries(bracket, next, bracket.seed(LEAGUE, waitingSeed), winner);
    }

    @Override
    public SeriesMvp seriesMvp(int round) {
        if (round == Round.PLAYOFF.getNumber()) {
            return new SeriesMvp(AwardType.KBO_PO_MVP, PostseasonService.LCS_MARKER);
        }
        if (round == Round.KOREAN_SERIES.getNumber()) {
            return new SeriesMvp(AwardType.KBO_KS_MVP, PostseasonService.WS_MARKER);
        }
        return null;
    }

    private static void addSeries(Bracket bracket, Round round, Seed high, Seed low) {
        Series series = new Series(round.getNumber(), LEAGUE, high, low);
        series.highWins = round.highSeedStartWins();             // 와일드카드 1승 어드밴티지
        bracket.series.add(series);
    }
}
