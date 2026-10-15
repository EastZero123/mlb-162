package com.mlbgame.simulation.game;

import com.mlbgame.player.Hand;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.BatterProfile;
import com.mlbgame.simulation.PaContext;
import com.mlbgame.simulation.PaProbabilities;
import com.mlbgame.simulation.PaResult;
import com.mlbgame.simulation.PitcherProfile;
import com.mlbgame.simulation.PlateAppearanceModel;
import com.mlbgame.simulation.SimulationBalance;
import com.mlbgame.simulation.game.UserHitter.HitterRole;
import com.mlbgame.simulation.game.UserPitcher.PitcherRole;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * 경기 한 판을 타석 단위로 진행한다. 양 팀 타자·투수는 팀 평균 능력치의 가상 선수이고,
 * 내 선수만 실제 능력치로 타석·등판·수비를 한다. 설계: docs/simulation.md 2장
 *
 * <p>랜덤은 넘겨받은 {@link RandomGenerator} 하나만 쓴다 → 같은 시드·같은 입력이면 같은 경기.
 */
@Component
public class GameSimulator {

    static final int LINEUP_SIZE = 9;
    static final int MAX_INNINGS = 25;
    /** 주자 식별자: 내 선수 */
    static final int USER = 99;
    static final int EMPTY = -1;

    // ---- 주루·수비 기본 확률 (MLB 평균 근사) ----
    static final double SCORE_FROM_SECOND_ON_SINGLE = 0.60;
    static final double FIRST_TO_THIRD_ON_SINGLE = 0.28;
    static final double SCORE_FROM_FIRST_ON_DOUBLE = 0.40;
    static final double DOUBLE_PLAY_ON_GROUNDER = 0.30;
    static final double SCORE_FROM_THIRD_ON_GROUNDER = 0.50;
    static final double ADVANCE_FROM_SECOND_ON_GROUNDER = 0.50;
    static final double SACRIFICE_FLY = 0.55;
    static final double ADVANCE_FROM_SECOND_ON_FLY = 0.30;
    static final double STEAL_ATTEMPT = 0.06;
    static final double STEAL_SUCCESS = 0.74;
    static final double OPPONENT_STEAL_ATTEMPT = 0.04;
    static final double OPPONENT_STEAL_SUCCESS = 0.75;
    static final double ERROR_RATE = 0.015;
    static final double OUTFIELD_ASSIST = 0.05;
    static final double LEFTY_STARTER_RATE = 0.28;

    // ---- 선발 투수 ----
    /** 투구 수 한도 = 60 + 체력/2 (체력 65 → 92구) */
    static final int PITCH_LIMIT_BASE = 60;
    /** 이 실점 이상이면 강판 */
    static final int STARTER_RUN_LIMIT = 6;
    /** 한도 15구 전부터 구위 저하 (능력치 단위) */
    static final double TIRED_PENALTY = -4;

    private final PlateAppearanceModel model;
    private final SimulationBalance balance;

    public GameSimulator(PlateAppearanceModel model, SimulationBalance balance) {
        this.model = model;
        this.balance = balance;
    }

    public GameResult simulate(GameSetup setup, RandomGenerator random) {
        return new Game(setup, random).play();
    }

    /** 수비 위치별로 인플레이 타구가 그 선수에게 갈 확률 */
    static double fieldingShare(Position position) {
        return switch (position) {
            case C -> 0.03;
            case B1 -> 0.10;
            case B2 -> 0.15;
            case B3 -> 0.12;
            case SS -> 0.16;
            case OF -> 0.12;
            default -> 0;
        };
    }

    static double shift(double p, double amount) {
        double logit = Math.log(p / (1 - p)) + amount;
        return 1 / (1 + Math.exp(-logit));
    }

    private record Mound(PitcherProfile profile, boolean user, double form) {
    }

    /** 경기 한 판의 진행 상태 */
    private final class Game {

        final GameSetup setup;
        final RandomGenerator rng;
        final int us;
        final int them;
        final UserHitter hitter;
        final UserPitcher pitcher;

        final int[] score = new int[2];          // 0 = 원정, 1 = 홈
        final int[] nextSlot = new int[2];
        final List<List<Integer>> lines = List.of(new ArrayList<>(), new ArrayList<>());
        final BatterProfile[][] lineups = new BatterProfile[2][LINEUP_SIZE];
        final PitcherProfile[] rotation = new PitcherProfile[2];   // 1~6회
        final PitcherProfile[] bullpen = new PitcherProfile[2];    // 7회 이후
        final Hand opponentStarterHand;

        final int[] bases = {EMPTY, EMPTY, EMPTY};
        int inning;
        int outs;
        boolean gameOver;
        boolean lastDoublePlay;
        boolean lastSacFly;

        // 내 선수
        final BattingLine bat = new BattingLine();
        final FieldingLine fld = new FieldingLine();
        final PitchingLine pit = new PitchingLine();
        final List<String> highlights = new ArrayList<>();
        boolean hitterAppeared;
        boolean pinchHitUsed;
        boolean starterActive;
        int userPitches;
        int userRunsAllowed;
        Integer starterExitDiff;
        boolean closerActive;
        boolean closerUsed;
        int closerEntryDiff;
        int closerExitDiff;

        Game(GameSetup setup, RandomGenerator rng) {
            this.setup = setup;
            this.rng = rng;
            this.us = setup.home() ? 1 : 0;
            this.them = 1 - us;
            this.hitter = setup.hitter();
            this.pitcher = setup.pitcher();
            for (int t = 0; t < 2; t++) {
                TeamRatings ratings = t == us ? setup.team() : setup.opponent();
                for (int i = 0; i < LINEUP_SIZE; i++) {
                    lineups[t][i] = BatterProfile.average(ratings.offense()).withBats(i % 3 == 1 ? Hand.L : Hand.R);
                }
                rotation[t] = PitcherProfile.average(ratings.rotation());
                bullpen[t] = PitcherProfile.average(ratings.bullpen());
            }
            this.opponentStarterHand = rng.nextDouble() < LEFTY_STARTER_RATE ? Hand.L : Hand.R;
            if (pitcher != null && pitcher.role() == PitcherRole.STARTER) {
                starterActive = true;
                pit.games = 1;
                pit.gamesStarted = 1;
            }
            if (hitter != null && hitter.starts()) {
                hitterAppeared = true;
                bat.games = 1;
            }
        }

        GameResult play() {
            int played = 0;
            for (inning = 1; inning <= MAX_INNINGS; inning++) {
                played = inning;
                halfInning(0);
                if (inning >= 9 && score[1] > score[0]) {
                    break;                       // 홈 팀이 앞서면 말 공격 없음
                }
                halfInning(1);
                if (gameOver || (inning >= 9 && score[0] != score[1])) {
                    break;
                }
            }
            if (score[0] == score[1]) {
                score[1]++;                      // 25회까지 동점이면 홈 팀 승 (안전장치)
            }
            decide();
            return new GameResult(score[us], score[them], played,
                    List.copyOf(lines.get(us)), List.copyOf(lines.get(them)),
                    participated(),
                    hitter != null && hitterAppeared ? bat : null,
                    hitter != null && hitter.starts() ? fld : null,
                    pitcher != null && pit.games > 0 ? pit : null,
                    List.copyOf(highlights));
        }

        boolean participated() {
            if (hitter != null) {
                return hitterAppeared;
            }
            return pitcher != null && pit.games > 0;
        }

        // ------------------------------------------------------------ 이닝

        void halfInning(int batting) {
            int defense = 1 - batting;
            outs = 0;
            bases[0] = bases[1] = bases[2] = EMPTY;
            if (inning >= 10) {
                // 연장 승부치기: 직전 타자가 2루 주자
                int previous = (nextSlot[batting] + LINEUP_SIZE - 1) % LINEUP_SIZE;
                bases[1] = batting == us && isUserStarterSlot(previous) ? USER : previous;
            }
            if (defense == us) {
                closerEntry();
            }
            int startScore = score[batting];
            while (outs < 3 && !gameOver) {
                if (batting == us) {
                    userStealAttempt();
                } else if (defense == us) {
                    catcherDefense();
                }
                if (outs >= 3) {
                    break;
                }
                plateAppearance(batting, defense);
            }
            lines.get(batting).add(score[batting] - startScore);
            if (defense == us && closerActive) {
                closerActive = false;
                closerExitDiff = score[us] - score[them];
            }
        }

        void closerEntry() {
            if (pitcher == null || closerUsed || inning < 9) {
                return;
            }
            int diff = score[us] - score[them];
            boolean enter = switch (pitcher.role()) {
                case CLOSER -> diff >= 0 && diff <= 3;      // 세이브 상황(1~3점 리드) 또는 동점
                case CLOSER_TUNE_UP -> true;                // 점검 등판
                default -> false;
            };
            if (enter) {
                closerActive = true;
                closerUsed = true;
                closerEntryDiff = diff;
                pit.games = 1;
            }
        }

        Mound mound(int defense) {
            if (defense == us) {
                if (starterActive || closerActive) {
                    double tired = starterActive && userPitches > pitchLimit() - 15 ? TIRED_PENALTY : 0;
                    return new Mound(pitcher.pitching(), true, pitcher.formOffset() + tired);
                }
                PitcherProfile staff = inning <= 6 ? rotation[us] : bullpen[us];
                return new Mound(staff.withThrows(Hand.R), false, 0);
            }
            if (inning <= 6) {
                return new Mound(rotation[them].withThrows(opponentStarterHand), false, 0);
            }
            return new Mound(bullpen[them].withThrows(inning % 2 == 0 ? Hand.R : Hand.L), false, 0);
        }

        int pitchLimit() {
            return PITCH_LIMIT_BASE + pitcher.stamina() / 2;
        }

        // ------------------------------------------------------------ 타석

        void plateAppearance(int batting, int defense) {
            int slot = nextSlot[batting];
            boolean userBatting = batting == us && userBatsNow(slot);
            BatterProfile batter = userBatting ? hitter.batting() : lineups[batting][slot];
            double batterForm = userBatting ? hitter.formOffset() : 0;
            Mound mound = mound(defense);
            boolean risp = bases[1] != EMPTY || bases[2] != EMPTY;

            PaProbabilities p = model.probabilities(batter, mound.profile(),
                    new PaContext(risp, batterForm, mound.form()));
            PaResult result = p.draw(rng);

            boolean userFielder = false;
            boolean error = false;
            if (defense == us && hitter != null && hitter.starts() && isBallInPlay(result)
                    && rng.nextDouble() < fieldingShare(hitter.position())) {
                userFielder = true;
                fld.chances++;
                double d = delta(hitter.fielding(), hitter.formOffset());
                if (result.isHit() && rng.nextDouble() < Math.max(0, 0.06 * d)) {
                    fld.hitsSaved++;
                    result = result == PaResult.SINGLE ? PaResult.GROUND_OUT : PaResult.FLY_OUT;
                } else if (result.isOut()) {
                    if (rng.nextDouble() < Math.max(0, -0.04 * d)) {
                        fld.hitsAllowed++;
                        result = PaResult.SINGLE;
                    } else if (rng.nextDouble() < shift(ERROR_RATE, -0.3 * d)) {
                        fld.errors++;
                        error = true;
                    }
                }
            }

            int diffBefore = score[us] - score[them];
            int outsBefore = outs;
            int runsBefore = score[batting];
            lastDoublePlay = false;
            lastSacFly = false;
            int batterId = userBatting ? USER : slot;

            if (error) {
                errorAdvance(batting, batterId);
            } else {
                advance(result, batting, batterId, userFielder);
            }
            int runs = score[batting] - runsBefore;
            if (batting == 1 && inning >= 9 && score[1] > score[0]) {
                gameOver = true;                 // 끝내기
            }
            nextSlot[batting] = (slot + 1) % LINEUP_SIZE;

            if (userBatting) {
                recordUserBatting(result, risp, error || lastDoublePlay ? 0 : runs, diffBefore);
            }
            if (mound.user()) {
                recordUserPitching(result, Math.min(3, outs) - outsBefore, runs);
            }
        }

        boolean userBatsNow(int slot) {
            if (hitter == null) {
                return false;
            }
            if (hitter.role() == HitterRole.STARTER) {
                return isUserStarterSlot(slot);
            }
            if (hitter.role() == HitterRole.PINCH_HITTER && !pinchHitUsed && inning >= 7
                    && slot == LINEUP_SIZE - 1) {
                pinchHitUsed = true;
                hitterAppeared = true;
                return true;
            }
            return false;
        }

        boolean isUserStarterSlot(int slot) {
            return hitter != null && hitter.starts() && slot == hitter.battingSlot() - 1;
        }

        boolean isBallInPlay(PaResult r) {
            return r == PaResult.SINGLE || r == PaResult.DOUBLE || r == PaResult.TRIPLE
                    || r == PaResult.GROUND_OUT || r == PaResult.FLY_OUT;
        }

        // ------------------------------------------------------------ 주루

        void advance(PaResult r, int t, int batterId, boolean userFielder) {
            switch (r) {
                case STRIKEOUT -> outs++;
                case WALK, HIT_BY_PITCH -> forceAdvance(t, batterId);
                case SINGLE -> single(t, batterId, userFielder);
                case DOUBLE -> {
                    scoreBase(2, t);
                    scoreBase(1, t);
                    if (bases[0] != EMPTY) {
                        int runner = bases[0];
                        bases[0] = EMPTY;
                        if (rng.nextDouble() < runnerChance(runner, t, SCORE_FROM_FIRST_ON_DOUBLE)) {
                            scoreRunner(runner, t);
                        } else {
                            bases[2] = runner;
                        }
                    }
                    bases[1] = batterId;
                }
                case TRIPLE -> {
                    scoreBase(2, t);
                    scoreBase(1, t);
                    scoreBase(0, t);
                    bases[2] = batterId;
                }
                case HOME_RUN -> {
                    scoreBase(2, t);
                    scoreBase(1, t);
                    scoreBase(0, t);
                    scoreRunner(batterId, t);
                }
                case GROUND_OUT -> grounder(t, batterId);
                case FLY_OUT -> flyBall(t);
            }
        }

        void single(int t, int batterId, boolean userFielder) {
            scoreBase(2, t);
            if (bases[1] != EMPTY) {
                int runner = bases[1];
                bases[1] = EMPTY;
                if (rng.nextDouble() < runnerChance(runner, t, SCORE_FROM_SECOND_ON_SINGLE)) {
                    boolean assist = userFielder && hitter.position() == Position.OF
                            && rng.nextDouble() < shift(OUTFIELD_ASSIST, 0.4 * delta(hitter.arm(), hitter.formOffset()));
                    if (assist) {
                        outs++;
                        fld.assists++;
                        highlights.add(inningLabel(1 - us) + " 홈 송구로 주자 아웃 (보살)");
                    } else {
                        scoreRunner(runner, t);
                    }
                } else {
                    bases[2] = runner;
                }
            }
            if (bases[0] != EMPTY) {
                int runner = bases[0];
                bases[0] = EMPTY;
                if (bases[2] == EMPTY && rng.nextDouble() < runnerChance(runner, t, FIRST_TO_THIRD_ON_SINGLE)) {
                    bases[2] = runner;
                } else {
                    bases[1] = runner;
                }
            }
            bases[0] = batterId;
        }

        void grounder(int t, int batterId) {
            if (bases[0] != EMPTY && outs < 2) {
                double chance = DOUBLE_PLAY_ON_GROUNDER;
                if (t == us && batterId == USER) {
                    chance = shift(chance, -0.25 * delta(hitter.batting().speed(), hitter.formOffset()));
                }
                if (rng.nextDouble() < chance) {
                    outs += 2;
                    lastDoublePlay = true;
                    bases[0] = EMPTY;
                    if (outs < 3) {
                        if (bases[2] != EMPTY && rng.nextDouble() < SCORE_FROM_THIRD_ON_GROUNDER) {
                            scoreBase(2, t);
                        }
                        if (bases[1] != EMPTY && bases[2] == EMPTY) {
                            bases[2] = bases[1];
                            bases[1] = EMPTY;
                        }
                    }
                    return;
                }
            }
            outs++;
            if (outs < 3) {
                if (bases[2] != EMPTY && rng.nextDouble() < SCORE_FROM_THIRD_ON_GROUNDER) {
                    scoreBase(2, t);
                }
                if (bases[1] != EMPTY && bases[2] == EMPTY && rng.nextDouble() < ADVANCE_FROM_SECOND_ON_GROUNDER) {
                    bases[2] = bases[1];
                    bases[1] = EMPTY;
                }
                if (bases[0] != EMPTY && bases[1] == EMPTY) {
                    bases[1] = bases[0];
                    bases[0] = EMPTY;
                }
            }
        }

        void flyBall(int t) {
            outs++;
            if (outs < 3 && bases[2] != EMPTY && rng.nextDouble() < SACRIFICE_FLY) {
                scoreBase(2, t);
                lastSacFly = true;
            }
            if (outs < 3 && bases[1] != EMPTY && bases[2] == EMPTY && rng.nextDouble() < ADVANCE_FROM_SECOND_ON_FLY) {
                bases[2] = bases[1];
                bases[1] = EMPTY;
            }
        }

        /** 볼넷·사구: 밀려나는 주자만 진루 */
        void forceAdvance(int t, int batterId) {
            if (bases[0] != EMPTY) {
                if (bases[1] != EMPTY) {
                    if (bases[2] != EMPTY) {
                        scoreBase(2, t);
                    }
                    bases[2] = bases[1];
                }
                bases[1] = bases[0];
            }
            bases[0] = batterId;
        }

        /** 실책 출루: 모든 주자 한 베이스씩 */
        void errorAdvance(int t, int batterId) {
            scoreBase(2, t);
            bases[2] = bases[1];
            bases[1] = bases[0];
            bases[0] = batterId;
        }

        void scoreBase(int base, int t) {
            if (bases[base] != EMPTY) {
                scoreRunner(bases[base], t);
                bases[base] = EMPTY;
            }
        }

        void scoreRunner(int runnerId, int t) {
            score[t]++;
            if (t == us && runnerId == USER) {
                bat.runs++;
            }
        }

        double runnerChance(int runnerId, int t, double base) {
            if (t == us && runnerId == USER) {
                return shift(base, 0.3 * delta(hitter.batting().speed(), hitter.formOffset()));
            }
            return base;
        }

        void userStealAttempt() {
            if (hitter == null || bases[0] != USER || bases[1] != EMPTY || outs >= 2) {
                return;
            }
            double d = delta(hitter.stealing(), hitter.formOffset());
            if (rng.nextDouble() >= shift(STEAL_ATTEMPT, 0.5 * d)) {
                return;
            }
            bases[0] = EMPTY;
            if (rng.nextDouble() < shift(STEAL_SUCCESS, 0.35 * d)) {
                bases[1] = USER;
                bat.stolenBases++;
            } else {
                outs++;
                bat.caughtStealing++;
            }
        }

        void catcherDefense() {
            if (hitter == null || !hitter.starts() || hitter.position() != Position.C
                    || bases[0] == EMPTY || bases[1] != EMPTY || outs >= 2
                    || rng.nextDouble() >= OPPONENT_STEAL_ATTEMPT) {
                return;
            }
            int runner = bases[0];
            bases[0] = EMPTY;
            double success = shift(OPPONENT_STEAL_SUCCESS, -0.4 * delta(hitter.arm(), hitter.formOffset()));
            if (rng.nextDouble() < success) {
                bases[1] = runner;
                fld.stolenBasesAgainst++;
            } else {
                outs++;
                fld.caughtStealing++;
            }
        }

        double delta(int rating, double form) {
            return (rating + form - balance.baselineRating()) / 10.0;
        }

        // ------------------------------------------------------------ 기록

        void recordUserBatting(PaResult result, boolean risp, int rbi, int diffBefore) {
            bat.games = 1;
            bat.plateAppearances++;
            boolean atBat = result.isAtBat() && !lastSacFly;
            if (atBat) {
                bat.atBats++;
                if (risp) {
                    bat.rispAtBats++;
                    if (result.isHit()) {
                        bat.rispHits++;
                    }
                }
            }
            switch (result) {
                case SINGLE -> bat.hits++;
                case DOUBLE -> {
                    bat.hits++;
                    bat.doubles++;
                }
                case TRIPLE -> {
                    bat.hits++;
                    bat.triples++;
                }
                case HOME_RUN -> {
                    bat.hits++;
                    bat.homeRuns++;
                }
                case WALK -> bat.walks++;
                case HIT_BY_PITCH -> bat.hitByPitch++;
                case STRIKEOUT -> bat.strikeouts++;
                default -> {
                }
            }
            if (lastSacFly) {
                bat.sacFlies++;
            }
            if (lastDoublePlay) {
                bat.groundIntoDoublePlay++;
            }
            bat.rbi += rbi;
            battingHighlight(result, rbi, diffBefore);
        }

        void battingHighlight(PaResult result, int rbi, int diffBefore) {
            int diffAfter = score[us] - score[them];
            String tag = "";
            if (gameOver) {
                tag = "끝내기 ";
            } else if (diffBefore < 0 && diffAfter > 0) {
                tag = "역전 ";
            } else if (diffBefore == 0 && diffAfter > 0 && inning >= 7) {
                tag = "균형을 깨는 ";
            } else if (diffBefore < 0 && diffAfter == 0) {
                tag = "동점 ";
            }
            String when = inningLabel(us);
            if (result == PaResult.HOME_RUN) {
                String kind = switch (rbi) {
                    case 1 -> "솔로";
                    case 2 -> "투런";
                    case 3 -> "스리런";
                    default -> "만루";
                };
                highlights.add(when + " " + tag + kind + " 홈런!");
            } else if (result.isHit() && rbi > 0 && !tag.isEmpty()) {
                String hit = switch (result) {
                    case DOUBLE -> "2루타";
                    case TRIPLE -> "3루타";
                    default -> "적시타";
                };
                highlights.add(when + " " + tag + hit + " (" + rbi + "타점)");
            }
        }

        void recordUserPitching(PaResult result, int outsMade, int runs) {
            pit.battersFaced++;
            int pitches = pitchCount(result);
            pit.pitches += pitches;
            userPitches += pitches;
            pit.outs += Math.max(0, outsMade);
            pit.runs += runs;
            userRunsAllowed += runs;
            switch (result) {
                case SINGLE, DOUBLE, TRIPLE -> pit.hits++;
                case HOME_RUN -> {
                    pit.hits++;
                    pit.homeRuns++;
                }
                case WALK -> pit.walks++;
                case STRIKEOUT -> pit.strikeouts++;
                default -> {
                }
            }
            if (starterActive && !gameOver
                    && (userPitches >= pitchLimit() || userRunsAllowed >= STARTER_RUN_LIMIT)) {
                starterActive = false;
                starterExitDiff = score[us] - score[them];
            }
        }

        /** 결과별 평균 투구 수 × 투구 효율 보정 ± 1 */
        int pitchCount(PaResult result) {
            double base = switch (result) {
                case STRIKEOUT -> 4.8;
                case WALK -> 5.6;
                case HIT_BY_PITCH -> 3.2;
                default -> 3.3;
            };
            double factor = 1 - 0.04 * delta(pitcher.efficiency(), pitcher.formOffset());
            return Math.max(1, (int) Math.round(base * factor) + rng.nextInt(3) - 1);
        }

        String inningLabel(int battingTeam) {
            return inning + "회" + (battingTeam == 0 ? "초" : "말");
        }

        // ------------------------------------------------------------ 승패·세이브

        void decide() {
            boolean win = score[us] > score[them];
            if (pitcher != null && pit.games > 0) {
                if (pitcher.role() == PitcherRole.STARTER) {
                    int exitDiff = starterExitDiff != null ? starterExitDiff : score[us] - score[them];
                    if (pit.outs >= 15 && exitDiff > 0 && win) {
                        pit.wins = 1;
                    } else if (exitDiff < 0 && !win) {
                        pit.losses = 1;
                    }
                    if (pit.outs >= 18 && pit.runs <= 3) {
                        pit.qualityStarts = 1;
                    }
                    String line = pit.summary();
                    if (pit.qualityStarts == 1) line += " 퀄리티스타트";
                    if (pit.wins == 1) line += " · 승리투수";
                    if (pit.losses == 1) line += " · 패전";
                    highlights.add(line);
                    if (pit.strikeouts >= 8 && !pitcher.repertoire().isEmpty()) {
                        String pitch = pitcher.repertoire().get(rng.nextInt(pitcher.repertoire().size())).getLabel();
                        highlights.add(pitch + "을(를) 앞세워 " + pit.strikeouts + "탈삼진 쇼");
                    }
                } else if (closerUsed) {
                    if (closerEntryDiff >= 1 && closerEntryDiff <= 3) {
                        if (closerExitDiff >= 1 && win) {
                            pit.saves = 1;
                            highlights.add(closerEntryDiff + "점 차 리드를 지켜내고 세이브");
                        } else if (closerExitDiff <= 0) {
                            pit.blownSaves = 1;
                            if (win) {
                                pit.wins = 1;
                            } else if (closerExitDiff < 0) {
                                pit.losses = 1;
                            }
                            highlights.add("블론세이브 (" + pit.summary() + ")");
                        }
                    } else if (closerEntryDiff == 0) {
                        if (win && closerExitDiff >= 0) {
                            pit.wins = 1;
                            highlights.add("동점 상황 등판, 구원승");
                        } else if (!win && closerExitDiff < 0) {
                            pit.losses = 1;
                        }
                    }
                }
            }
            if (hitter != null && hitterAppeared && (bat.hits >= 3 || bat.rbi >= 4 || bat.homeRuns >= 2)) {
                highlights.add(bat.summary() + " 맹타");
            }
            if (fld.caughtStealing > 0) {
                highlights.add("도루 저지 " + fld.caughtStealing + "회");
            }
        }
    }
}
