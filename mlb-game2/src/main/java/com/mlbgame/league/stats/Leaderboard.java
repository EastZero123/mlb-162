package com.mlbgame.league.stats;

import com.mlbgame.common.Messages;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * 리더보드 계산. 비율 기록은 규정 타석·이닝을 채운 선수만 (MLB 규정: 팀 경기 × 3.1 타석, × 1.0 이닝).
 */
public final class Leaderboard {

    public static final double QUALIFY_PA_PER_GAME = 3.1;
    public static final double QUALIFY_IP_PER_GAME = 1.0;
    public static final int TOP = 5;

    private Leaderboard() {
    }

    /** 순위 후보 — NPC 또는 내 선수 */
    public record Candidate(String name, String teamName, BattingLine batting, PitchingLine pitching, boolean me) {
    }

    public record Entry(int rank, Candidate candidate, double value) {
    }

    public enum Category {
        AVG("타율", true, false),
        HR("홈런", true, false),
        RBI("타점", true, false),
        OPS("OPS", true, false),
        SB("도루", true, false),
        ERA("평균자책점", false, true),
        WINS("승", false, false),
        STRIKEOUTS("탈삼진", false, false),
        SAVES("세이브", false, false);

        private final String label;
        private final boolean batting;
        private final boolean ascending;

        Category(String label, boolean batting, boolean ascending) {
            this.label = label;
            this.batting = batting;
            this.ascending = ascending;
        }

        public String getLabel() {
            return Messages.text("Leaderboard.Category." + name(), label);
        }

        public boolean isRate() {
            return this == AVG || this == OPS || this == ERA;
        }
    }

    public static List<Entry> top(List<Candidate> candidates, Category category, int teamGames) {
        ToDoubleFunction<Candidate> value = valueOf(category);
        Predicate<Candidate> eligible = c -> category.batting ? c.batting() != null : c.pitching() != null;
        Predicate<Candidate> qualified = c -> !category.isRate() || (category.batting
                ? c.batting().plateAppearances >= teamGames * QUALIFY_PA_PER_GAME
                : c.pitching().outs / 3.0 >= teamGames * QUALIFY_IP_PER_GAME);
        Comparator<Candidate> order = Comparator.comparingDouble(value);
        if (!category.ascending) {
            order = order.reversed();
        }
        List<Candidate> sorted = candidates.stream()
                .filter(eligible)
                .filter(qualified)
                .filter(c -> category.isRate() || value.applyAsDouble(c) > 0)
                .sorted(order)
                .limit(TOP)
                .toList();
        return IntStream.range(0, sorted.size())
                .mapToObj(i -> new Entry(i + 1, sorted.get(i), value.applyAsDouble(sorted.get(i))))
                .toList();
    }

    static ToDoubleFunction<Candidate> valueOf(Category category) {
        return switch (category) {
            case AVG -> c -> c.batting().getAverage();
            case HR -> c -> c.batting().homeRuns;
            case RBI -> c -> c.batting().rbi;
            case OPS -> c -> c.batting().getOps();
            case SB -> c -> c.batting().stolenBases;
            case ERA -> c -> c.pitching().getEra();
            case WINS -> c -> c.pitching().wins;
            case STRIKEOUTS -> c -> c.pitching().strikeouts;
            case SAVES -> c -> c.pitching().saves;
        };
    }
}
