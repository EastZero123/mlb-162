package com.mlbgame.simulation.stats;

import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.world.RosterRole;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.simulation.BatterProfile;
import com.mlbgame.simulation.PaContext;
import com.mlbgame.simulation.PaProbabilities;
import com.mlbgame.simulation.PaResult;
import com.mlbgame.simulation.PitcherProfile;
import com.mlbgame.simulation.PlateAppearanceModel;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.SimulationBalance.Baseline;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * NPC 선수 기록을 "통계로" 생성한다 (경기를 돌리지 않음). 설계: docs/league-world.md 5장
 *
 * <p>타석·타자 단위로 M2 타석 모델의 확률을 뽑아 기본 기록을 만들고,
 * 득점·타점·실점처럼 경기 흐름이 필요한 값은 Base Runs 추정치에 약간의 운을 섞는다.
 * 상대는 리그 평균(65) 선수로 본다.
 */
@Component
public class NpcStatGenerator {

    static final int LEAGUE_AVERAGE = 65;
    static final double LINEUP_PLAY_RATE = 0.93;
    static final double LINEUP_PA_PER_GAME = 4.2;
    static final double BENCH_PLAY_RATE = 0.45;
    static final double BENCH_PA_PER_GAME = 1.6;
    static final int ROTATION_SIZE = 5;
    static final double RELIEVER_APPEARANCE_RATE = 0.40;
    static final double CLOSER_APPEARANCE_RATE = 0.42;
    static final double CLOSER_SAVE_SITUATION = 0.62;
    /**
     * 투수 실점 보정: 등판(6이닝 안팎) 단위 Base Runs 는 작은 표본이라 실점을 부풀린다 (식의 B/(B+C) 가 볼록).
     * 같은 능력치 내 선수(경기 시뮬레이션) 대비 NPC ERA 가 OVR 65~90 전 구간에서 1.12~1.21배라 (M5-3 시상 공정성 검증)
     * 평균 선발 ERA 가 리그 평균(4.2~4.5)에 오도록 맞춘 값
     */
    static final double RUN_SCALE = 0.87;

    /** 득점권 타석 비율 (MLB 약 28%) — 이 타석에는 클러치·위기 관리 보정이 붙는다 (경기 시뮬레이션과 맞춤) */
    static final double RISP_SHARE = 0.28;
    static final PaContext RISP = new PaContext(true, 0, 0);

    /**
     * 주전의 출전율·경기당 타석은 OVR 이 높을수록 많다 — 경기 시뮬레이션에서 좋은 타자는 타순이 앞이고 매 경기 나가므로
     * (M5-3 시상 공정성 검증: OVR 85 내 선수 762타석 vs NPC 632타석)
     */
    static double lineupPlayRate(double overall) {
        return clamp(LINEUP_PLAY_RATE + 0.002 * (overall - LEAGUE_AVERAGE), 0.88, 0.97);
    }

    static double lineupPaPerGame(double overall) {
        return clamp(LINEUP_PA_PER_GAME + 0.03 * (overall - LEAGUE_AVERAGE), 3.9, 4.75);
    }

    private PaContext context(RandomGenerator rng) {
        PaContext context = rng.nextDouble() < RISP_SHARE ? RISP : PaContext.NEUTRAL;
        return baseline == null ? context : context.withBaseline(baseline);
    }

    private final PlateAppearanceModel model;
    /** 리그 평균 기록 (null 이면 MLB) — {@link #forMode} */
    private final Baseline baseline;

    @Autowired
    public NpcStatGenerator(PlateAppearanceModel model) {
        this(model, null);
    }

    private NpcStatGenerator(PlateAppearanceModel model, Baseline baseline) {
        this.model = model;
        this.baseline = baseline;
    }

    /** 이 모드의 리그 평균 기록으로 생성하는 생성기 */
    public NpcStatGenerator forMode(LeagueMode mode) {
        return mode == LeagueMode.MLB ? this : new NpcStatGenerator(model, model.baseline(mode));
    }

    public record HitterLine(BattingLine batting, FieldingLine fielding) {
    }

    // ------------------------------------------------------------ 타자

    public HitterLine hitter(PlayerSnapshot player, RosterRole role, int teamGames, RandomGenerator rng) {
        boolean regular = role == RosterRole.LINEUP;
        double overall = player.overall();
        int games = binomial(teamGames, regular ? lineupPlayRate(overall) : BENCH_PLAY_RATE, rng);
        int pa = (int) Math.round(games * (regular ? lineupPaPerGame(overall) : BENCH_PA_PER_GAME));

        BatterProfile batter = BatterProfile.of(player);
        BattingLine line = new BattingLine();
        line.games = games;
        double stealDelta = delta(player.attribute(Attribute.STEALING));
        for (int i = 0; i < pa; i++) {
            PitcherProfile pitcher = PitcherProfile.average(LEAGUE_AVERAGE).withThrows(rng.nextInt(100) < 72 ? Hand.R : Hand.L);
            PaProbabilities p = model.probabilities(batter, pitcher, context(rng));
            PaResult r = p.draw(rng);
            line.plateAppearances++;
            if (r.isAtBat()) line.atBats++;
            switch (r) {
                case SINGLE -> line.hits++;
                case DOUBLE -> { line.hits++; line.doubles++; }
                case TRIPLE -> { line.hits++; line.triples++; }
                case HOME_RUN -> { line.hits++; line.homeRuns++; }
                case WALK -> line.walks++;
                case HIT_BY_PITCH -> line.hitByPitch++;
                case STRIKEOUT -> line.strikeouts++;
                default -> { }
            }
            boolean onFirst = r == PaResult.SINGLE || r == PaResult.WALK || r == PaResult.HIT_BY_PITCH;
            if (onFirst && rng.nextDouble() < shift(0.06, 0.5 * stealDelta)) {
                if (rng.nextDouble() < shift(0.74, 0.35 * stealDelta)) line.stolenBases++;
                else line.caughtStealing++;
            }
        }
        double runsCreated = baseRuns(line);
        line.runs = (int) Math.round(runsCreated * (0.85 + 0.3 * rng.nextDouble()));
        line.rbi = (int) Math.round(runsCreated * (0.8 + 0.3 * rng.nextDouble()) + line.homeRuns * 0.3);
        line.rispAtBats = (int) Math.round(line.atBats * 0.25);
        line.rispHits = binomial(line.rispAtBats, line.getAverage(), rng);

        return new HitterLine(line, fielding(player, games, rng));
    }

    private FieldingLine fielding(PlayerSnapshot player, int games, RandomGenerator rng) {
        FieldingLine f = new FieldingLine();
        double d = delta(player.attribute(Attribute.FIELDING));
        f.chances = (int) Math.round(games * 4.5);
        f.hitsSaved = binomial(f.chances, Math.max(0, 0.06 * d) * 0.3, rng);
        f.hitsAllowed = binomial(f.chances, Math.max(0, -0.04 * d) * 0.3, rng);
        f.errors = binomial(f.chances, shift(0.015, -0.3 * d), rng);
        f.assists = binomial(games, shift(0.02, 0.4 * delta(player.attribute(Attribute.ARM))), rng);
        return f;
    }

    // ------------------------------------------------------------ 투수

    /**
     * @param rotationSlot 선발이면 1~5선발 (앞 순번이 등판을 조금 더 함), 그 외 0
     * @param teamWinPct   팀 승률 — 승패·세이브 기회에 반영
     */
    public PitchingLine pitcher(PlayerSnapshot player, RosterRole role, int rotationSlot, int teamGames,
                                double teamWinPct, RandomGenerator rng) {
        PitcherProfile pitcher = PitcherProfile.of(player);
        PitchingLine line = new PitchingLine();
        switch (role) {
            case ROTATION -> {
                int starts = teamGames / ROTATION_SIZE + (rotationSlot <= teamGames % ROTATION_SIZE ? 1 : 0);
                int limit = 60 + player.attribute(Attribute.STAMINA) / 2;
                double perBatter = 3.85 * (1 - 0.04 * delta(player.attribute(Attribute.EFFICIENCY)));
                int battersPerStart = Math.max(12, (int) Math.round(limit / perBatter));
                for (int s = 0; s < starts; s++) {
                    PitchingLine start = outing(pitcher, battersPerStart, 27, rng);
                    start.games = 1;
                    start.gamesStarted = 1;
                    double ra9 = start.getEra();
                    if (rng.nextDouble() < 0.72) {          // 승패가 붙는 선발
                        double winChance = clamp(teamWinPct + (4.4 - ra9) * 0.05, 0.1, 0.9);
                        if (rng.nextDouble() < winChance) start.wins = 1; else start.losses = 1;
                    }
                    if (start.outs >= 18 && start.runs <= 3) start.qualityStarts = 1;
                    line.add(start);
                }
            }
            case BULLPEN, CLOSER -> {
                double rate = role == RosterRole.CLOSER ? CLOSER_APPEARANCE_RATE : RELIEVER_APPEARANCE_RATE;
                int appearances = binomial(teamGames, rate, rng);
                for (int a = 0; a < appearances; a++) {
                    PitchingLine outing = outing(pitcher, 99, 3, rng);   // 아웃 3개를 잡을 때까지
                    outing.games = 1;
                    if (role == RosterRole.CLOSER && rng.nextDouble() < CLOSER_SAVE_SITUATION * (0.6 + teamWinPct * 0.8)) {
                        if (outing.runs == 0 || rng.nextDouble() < 0.55) outing.saves = 1;
                        else outing.blownSaves = 1;
                    } else if (rng.nextDouble() < 0.12) {
                        if (rng.nextDouble() < teamWinPct) outing.wins = 1; else outing.losses = 1;
                    }
                    line.add(outing);
                }
            }
            default -> { }
        }
        return line;
    }

    /** 타자를 상대해 maxOuts 아웃을 잡거나 maxBatters 명을 상대할 때까지 */
    private PitchingLine outing(PitcherProfile pitcher, int maxBatters, int maxOuts, RandomGenerator rng) {
        PitchingLine line = new PitchingLine();
        BattingLine against = new BattingLine();
        for (int b = 0; b < maxBatters && line.outs < maxOuts; b++) {
            BatterProfile batter = BatterProfile.average(LEAGUE_AVERAGE).withBats(rng.nextInt(100) < 60 ? Hand.R : Hand.L);
            PaResult r = model.probabilities(batter, pitcher, context(rng)).draw(rng);
            line.battersFaced++;
            against.plateAppearances++;
            if (r.isAtBat()) against.atBats++;
            switch (r) {
                case STRIKEOUT -> { line.outs++; line.strikeouts++; }
                case GROUND_OUT, FLY_OUT -> line.outs++;
                case WALK -> { line.walks++; against.walks++; }
                case HIT_BY_PITCH -> against.hitByPitch++;
                case SINGLE -> { line.hits++; against.hits++; }
                case DOUBLE -> { line.hits++; against.hits++; against.doubles++; }
                case TRIPLE -> { line.hits++; against.hits++; against.triples++; }
                case HOME_RUN -> { line.hits++; line.homeRuns++; against.hits++; against.homeRuns++; }
            }
        }
        line.pitches = (int) Math.round(line.battersFaced * 3.85);
        line.runs = (int) Math.round(baseRuns(against) * RUN_SCALE * (0.8 + 0.4 * rng.nextDouble()));
        return line;
    }

    // ------------------------------------------------------------ 공통

    /** Base Runs 득점 추정: A × B / (B + C) + D */
    static double baseRuns(BattingLine l) {
        int singles = l.hits - l.doubles - l.triples - l.homeRuns;
        double totalBases = singles + 2.0 * l.doubles + 3.0 * l.triples + 4.0 * l.homeRuns;
        double a = l.hits + l.walks + l.hitByPitch - l.homeRuns;
        double b = (1.4 * totalBases - 0.6 * l.hits - 3 * l.homeRuns + 0.1 * (l.walks + l.hitByPitch)) * 1.02;
        double c = l.atBats - l.hits;
        double d = l.homeRuns;
        return (b + c) <= 0 ? d : a * b / (b + c) + d;
    }

    static int binomial(int n, double p, RandomGenerator rng) {
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() < p) k++;
        }
        return k;
    }

    private static double delta(int rating) {
        return (rating - LEAGUE_AVERAGE) / 10.0;
    }

    private static double shift(double p, double amount) {
        double logit = Math.log(p / (1 - p)) + amount;
        return 1 / (1 + Math.exp(-logit));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
