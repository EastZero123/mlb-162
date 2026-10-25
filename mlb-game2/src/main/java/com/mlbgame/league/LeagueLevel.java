package com.mlbgame.league;

import com.mlbgame.common.Messages;
import java.util.Arrays;
import java.util.List;

/**
 * 리그 레벨. 모드마다 하위 → 최상위 순서로 승격한다 (MLB: 루키 → 싱글A → 더블A → 트리플A → 메이저리그).
 * 마이너리그 산하팀은 실명 대신 "메이저 구단명 + 접미사"로 표시한다. 예) 디트로이트 타이거스 AA
 * DB 에는 enum 이름으로 저장하므로 값을 추가해도 마이그레이션이 필요 없다 (VARCHAR(10)).
 */
public enum LeagueLevel {
    ROOKIE("루키리그", "루키", 35, 35, LeagueMode.MLB, false),
    A("싱글A", "A", 45, 45, LeagueMode.MLB, false),
    AA("더블A", "AA", 52, 52, LeagueMode.MLB, false),
    AAA("트리플A", "AAA", 58, 58, LeagueMode.MLB, false),
    MLB("메이저리그", "", 65, 65, LeagueMode.MLB, true),
    // KBO: 퓨처스(2군) → 1군. 레벨 차이가 MLB 트리플A↔메이저(7)보다 크지 않게 퓨처스 평균 55 (docs/kbo-mode.md 3.3)
    // 퓨처스는 육성 리그라 루키·싱글A 가 없어도 신인이 뛸 수 있게 주전 기준을 낮춘다 (K4: 고졸 OVR 30 → 플래툰)
    FUTURES("퓨처스리그", "퓨처스", 55, 36, LeagueMode.KBO, false),
    KBO("1군", "", 65, 65, LeagueMode.KBO, true);

    private final String label;
    private final String teamSuffix;
    private final int averageRating;
    private final int regularRating;
    private final LeagueMode mode;
    private final boolean top;

    LeagueLevel(String label, String teamSuffix, int averageRating, int regularRating, LeagueMode mode, boolean top) {
        this.label = label;
        this.teamSuffix = teamSuffix;
        this.averageRating = averageRating;
        this.regularRating = regularRating;
        this.mode = mode;
        this.top = top;
    }

    /** 팀 내 입지(주전·플래툰·벤치) 기준 OVR — 보통은 레벨 평균, 퓨처스는 육성 리그라 더 낮다 */
    public int getRegularRating() {
        return regularRating;
    }

    /** 이 모드의 레벨 (하위 → 최상위) */
    public static List<LeagueLevel> of(LeagueMode mode) {
        return Arrays.stream(values()).filter(l -> l.mode == mode).toList();
    }

    /** 해당 레벨 선수들의 평균 능력치 (NPC 상대 생성, 출전 판정 기준) */
    public int getAverageRating() {
        return averageRating;
    }

    public String getLabel() {
        return Messages.text("LeagueLevel." + name(), label);
    }

    /** 팀명 뒤에 붙는 레벨 표기 (최상위 레벨은 빈 문자열) */
    public String getTeamSuffix() {
        return Messages.text("LeagueLevel." + name() + ".suffix", teamSuffix);
    }

    public LeagueMode getMode() {
        return mode;
    }

    /** 그 모드의 최상위 레벨(메이저리그·KBO 1군)인지 */
    public boolean isTop() {
        return top;
    }
}
