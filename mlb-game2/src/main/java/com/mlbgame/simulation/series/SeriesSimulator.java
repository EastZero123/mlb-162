package com.mlbgame.simulation.series;

import com.mlbgame.common.Messages;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.FieldingLine;
import com.mlbgame.simulation.game.GameResult;
import com.mlbgame.simulation.game.GameSetup;
import com.mlbgame.simulation.game.GameSimulator;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.game.UserHitter;
import com.mlbgame.simulation.game.UserHitter.HitterRole;
import com.mlbgame.simulation.game.UserPitcher;
import com.mlbgame.simulation.game.UserPitcher.PitcherRole;
import com.mlbgame.simulation.rules.Condition;
import com.mlbgame.simulation.rules.Fatigue;
import com.mlbgame.simulation.rules.InjuryRules;
import com.mlbgame.simulation.rules.InjuryRules.Injury;
import com.mlbgame.simulation.rules.LineupRules;
import com.mlbgame.simulation.rules.PitcherUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * 시리즈(기본 3경기) 진행. 설계: docs/simulation.md 4장
 *
 * <p>난수 사용 순서가 고정되어 있어 같은 {@link SeriesInput} 이면 항상 같은 결과가 나온다:
 * 컨디션 → (경기마다) 출전 판정 → 경기 → 부상 판정.
 */
@Component
public class SeriesSimulator {

    private final GameSimulator gameSimulator;

    public SeriesSimulator(GameSimulator gameSimulator) {
        this.gameSimulator = gameSimulator;
    }

    public SeriesResult simulate(SeriesInput input) {
        RandomGenerator rng = new SplittableRandom(input.seed());
        PlayerSnapshot player = input.player();
        boolean isPitcher = player.position().isPitcher();
        boolean isStarter = player.position() == Position.SP;
        int durability = player.attribute(Attribute.DURABILITY);
        double overall = player.overall();

        Condition condition = Condition.roll(rng);
        int fatigue = input.fatigue();
        PitcherUsage usage = input.usage() != null ? input.usage() : PitcherUsage.seasonStart();
        Injury injury = null;

        List<GameResult> games = new ArrayList<>();
        List<String> highlights = new ArrayList<>();
        BattingLine batting = isPitcher ? null : new BattingLine();
        FieldingLine fielding = isPitcher ? null : new FieldingLine();
        PitchingLine pitching = isPitcher ? new PitchingLine() : null;
        int wins = 0;

        for (int g = 1; g <= input.games(); g++) {
            double form = Fatigue.formOffset(condition, fatigue);
            HitterRole hitterRole = HitterRole.NONE;
            PitcherRole pitcherRole = PitcherRole.NONE;
            GameSetup setup;

            boolean available = injury == null && !input.sidelined();
            if (!isPitcher) {
                if (available) {
                    hitterRole = LineupRules.roleForGame(overall, input.level(), fatigue, rng);
                }
                int slot = LineupRules.battingSlot(player, input.level());
                setup = GameSetup.forHitter(input.home(), input.team(), input.opponent(),
                        UserHitter.of(player, slot, hitterRole, form));
            } else {
                if (available) {
                    pitcherRole = usage.roleForGame(isStarter, player.attribute(Attribute.RECOVERY), overall, input.level());
                }
                double pitcherForm = form;
                if (pitcherRole == PitcherRole.CLOSER || pitcherRole == PitcherRole.CLOSER_TUNE_UP) {
                    pitcherForm += usage.closerFatigueOffset(player.attribute(Attribute.STAMINA));
                }
                setup = GameSetup.forPitcher(input.home(), input.team(), input.opponent(),
                        UserPitcher.of(player, pitcherRole, pitcherForm));
            }

            GameResult result = gameSimulator.simulate(setup, rng, input.level().getMode());   // 레벨의 모드 = 리그 평균 기록
            games.add(result);
            if (result.isWin()) {
                wins++;
            }
            for (String line : result.highlights()) {
                highlights.add(Messages.get("hl.game", g, line));
            }

            boolean appeared = result.participated();
            if (!isPitcher) {
                if (result.batting() != null) batting.add(result.batting());
                if (result.fielding() != null) fielding.add(result.fielding());
                if (appeared) {
                    int gain = hitterRole == HitterRole.STARTER ? Fatigue.HITTER_START : Fatigue.HITTER_PINCH;
                    fatigue = Fatigue.add(fatigue, Fatigue.gain(gain, durability));
                } else {
                    fatigue = Fatigue.recover(fatigue, Fatigue.RECOVER_PER_DAY_OFF, durability);
                }
            } else {
                int pitches = appeared ? result.pitching().pitches : 0;
                if (appeared) {
                    pitching.add(result.pitching());
                    int gain = isStarter ? pitches / 10 * Fatigue.STARTER_PER_10_PITCHES : Fatigue.CLOSER_APPEARANCE;
                    fatigue = Fatigue.add(fatigue, Fatigue.gain(gain, durability));
                } else {
                    fatigue = Fatigue.recover(fatigue, Fatigue.RECOVER_PER_DAY_OFF, durability);
                }
                usage = usage.afterGame(appeared, appeared && pitcherRole == PitcherRole.STARTER, pitches);
            }

            // 부상 판정: 출전하지 않았으면 역할 NONE → 확률 0 (난수는 항상 같은 횟수로 소비)
            if (injury == null) {
                Optional<Injury> rolled = isPitcher
                        ? InjuryRules.rollForPitcher(appeared ? pitcherRole : PitcherRole.NONE, durability, fatigue, rng)
                        : InjuryRules.rollForHitter(appeared ? hitterRole : HitterRole.NONE, durability, fatigue, rng);
                if (rolled.isPresent()) {
                    injury = rolled.get();
                    String out = injury.isSeasonEnding()
                            ? Messages.get("injury.seasonEnding")
                            : Messages.get("injury.seriesOut", injury.seriesOut());
                    highlights.add(Messages.get("hl.injury", g, injury.severity().getLabel(), out));
                }
            }
        }

        return new SeriesResult(List.copyOf(games), wins, input.games() - wins, batting, fielding, pitching,
                condition, fatigue, usage, injury, List.copyOf(highlights));
    }

    /** 다음 시리즈 시작 상태: 시리즈 사이 이동일 회복 + 마무리 연투 기록 초기화 */
    public static CarryOver carryOver(SeriesResult result, int durability) {
        return new CarryOver(
                Fatigue.recover(result.endFatigue(), Fatigue.RECOVER_BETWEEN_SERIES, durability),
                PitcherUsage.restAfterSeries(result.endUsage()));
    }

    /** 시리즈 사이에 넘겨줄 상태 */
    public record CarryOver(int fatigue, PitcherUsage usage) {
    }
}
