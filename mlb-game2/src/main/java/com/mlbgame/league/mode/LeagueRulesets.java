package com.mlbgame.league.mode;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.player.Player;
import java.util.EnumMap;
import java.util.Map;

/**
 * 모드 → 규칙셋. 규칙셋은 상태 없는 순수 규칙이라 모드당 하나를 정적으로 공유한다
 * (서비스 생성자를 바꾸지 않고 어디서든 선수·월드·레벨의 모드로 찾는다).
 */
public final class LeagueRulesets {

    public static final LeagueRuleset MLB = new MlbRuleset();
    public static final LeagueRuleset KBO = new KboRuleset();

    private static final Map<LeagueMode, LeagueRuleset> RULESETS = new EnumMap<>(LeagueMode.class);

    static {
        RULESETS.put(LeagueMode.MLB, MLB);
        RULESETS.put(LeagueMode.KBO, KBO);
    }

    private LeagueRulesets() {
    }

    /** 규칙이 준비된 모드인가 (아니면 탭은 보이지만 선수를 만들 수 없다) */
    public static boolean isAvailable(LeagueMode mode) {
        return RULESETS.containsKey(mode);
    }

    public static LeagueRuleset of(LeagueMode mode) {
        LeagueRuleset ruleset = RULESETS.get(mode);
        if (ruleset == null) {
            throw new IllegalStateException(mode.getLabel() + " 모드 규칙이 아직 없습니다.");
        }
        return ruleset;
    }

    public static LeagueRuleset of(Player player) {
        return of(player.getLeagueMode());
    }

    public static LeagueRuleset of(LeagueWorld world) {
        return of(world.getLeagueMode());
    }

    public static LeagueRuleset of(LeagueLevel level) {
        return of(level.getMode());
    }
}
