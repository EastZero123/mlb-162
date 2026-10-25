package com.mlbgame.award;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.league.world.RosterRole;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.simulation.stats.NpcStatGenerator;
import com.mlbgame.simulation.stats.NpcStatGenerator.HitterLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * 마이너리그 시상 비교군. 마이너는 개별 선수가 없으므로, 레벨별로 구단마다 유망주 한 명씩(30명)을
 * 가상으로 만들어 같은 기간 기록을 생성한다. 능력치 = 레벨 평균 + 정규분포 × 6 (각 레벨의 기대주).
 */
@Component
public class MinorLeaguePool {

    static final int POOL_SIZE = 30;
    static final double SPREAD = 6;
    static final double PROSPECT_BONUS = 3;

    private final NpcStatGenerator generator;

    public MinorLeaguePool(NpcStatGenerator generator) {
        this.generator = generator;
    }

    /** 비교군 타자 점수 목록 */
    public List<Double> hitterScores(long seed, LeagueLevel level, int teamGames) {
        RandomGenerator rng = new SplittableRandom(seed);
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < POOL_SIZE; i++) {
            HitterLine line = generator.forMode(level.getMode()).hitter(snapshot(rng, Position.OF, level), RosterRole.LINEUP, teamGames, rng);
            scores.add(LeagueRulesets.of(level).awards().scoring().hitter(line.batting(), line.fielding()));
        }
        return scores;
    }

    /** 비교군 투수 점수 목록 (선발 또는 마무리) */
    public List<Double> pitcherScores(long seed, LeagueLevel level, int teamGames, boolean starter) {
        RandomGenerator rng = new SplittableRandom(seed);
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < POOL_SIZE; i++) {
            Position position = starter ? Position.SP : Position.CP;
            PitchingLine line = generator.forMode(level.getMode()).pitcher(snapshot(rng, position, level),
                    starter ? RosterRole.ROTATION : RosterRole.CLOSER, 1 + i % 5, teamGames, 0.5, rng);
            AwardRules.Scoring scoring = LeagueRulesets.of(level).awards().scoring();
            scores.add(starter ? scoring.starter(line) : scoring.reliever(line));
        }
        return scores;
    }

    private static PlayerSnapshot snapshot(RandomGenerator rng, Position position, LeagueLevel level) {
        int center = (int) Math.round(level.getAverageRating() + PROSPECT_BONUS + rng.nextGaussian() * SPREAD);
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.forPosition(position)) {
            attributes.put(a, Math.max(20, Math.min(99, center + (int) Math.round(rng.nextGaussian() * 5))));
        }
        return new PlayerSnapshot(position, Hand.R, Hand.R, attributes, List.of());
    }
}
