package com.mlbgame.combine;

import com.mlbgame.common.Messages;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import com.mlbgame.player.Position;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

/**
 * 컴바인 종목. 종목 점수(1~100)는 관련 능력치 평균 + 당일 컨디션 편차이고,
 * 화면에 보여줄 실측 기록(초, mph, km/h …)은 점수에서 환산한다.
 */
public enum CombineTest {

    // 야수
    DASH_60("60야드 대시", "초", 2, true,
            p -> p.attribute(Attribute.SPEED),
            s -> 7.6 - s * 0.013),
    BATTING_PRACTICE("배팅 프랙티스 타구 속도", "mph", 1, false,
            p -> avg(p, Attribute.POWER, Attribute.GAP_POWER),
            s -> 80 + s * 0.35),
    FIELDING_DRILL("수비 드릴 처리율", "%", 0, false,
            p -> p.attribute(Attribute.FIELDING),
            s -> 50 + s * 0.5),
    POP_TIME("포수 팝타임", "초", 2, true,
            p -> avg(p, Attribute.FIELDING, Attribute.ARM),
            s -> 2.30 - s * 0.004),
    THROWING("송구 속도", "mph", 1, false,
            p -> p.attribute(Attribute.ARM),
            s -> 70 + s * 0.3),

    // 투수
    VELOCITY("최고 구속", "km/h", 1, false,
            p -> p.attribute(Attribute.VELOCITY),
            s -> 130 + (s - 1) * 35.0 / 99),
    CONTROL("제구 테스트 (20구 중 스트라이크)", "구", 0, false,
            p -> p.attribute(Attribute.CONTROL),
            s -> Math.round(6 + s * 0.14)),
    BULLPEN("불펜 피칭 스카우트 평가", "점", 0, false,
            p -> avg(p, Attribute.PUT_AWAY, Attribute.MOVEMENT),
            s -> Math.round(20 + s * 0.6)),
    STAMINA_TEST("구위 유지 투구 수", "구", 0, false,
            p -> p.attribute(Attribute.STAMINA),
            s -> Math.round(40 + s * 0.8));

    private final String label;
    private final String unit;
    private final int decimals;
    private final boolean lowerIsBetter;
    private final ToDoubleFunction<Player> base;
    private final DoubleUnaryOperator recordFromScore;

    CombineTest(String label, String unit, int decimals, boolean lowerIsBetter,
                ToDoubleFunction<Player> base, DoubleUnaryOperator recordFromScore) {
        this.label = label;
        this.unit = unit;
        this.decimals = decimals;
        this.lowerIsBetter = lowerIsBetter;
        this.base = base;
        this.recordFromScore = recordFromScore;
    }

    /** 포지션별 컴바인 종목 4개 */
    public static List<CombineTest> forPosition(Position position) {
        if (position.isPitcher()) {
            return List.of(VELOCITY, CONTROL, BULLPEN, STAMINA_TEST);
        }
        CombineTest fielding = position == Position.C ? POP_TIME : FIELDING_DRILL;
        return List.of(DASH_60, BATTING_PRACTICE, fielding, THROWING);
    }

    public double baseScore(Player player) {
        return base.applyAsDouble(player);
    }

    public double recordOf(int score) {
        double scale = Math.pow(10, decimals);
        return Math.round(recordFromScore.applyAsDouble(score) * scale) / scale;
    }

    private static double avg(Player player, Attribute... attributes) {
        double sum = 0;
        for (Attribute attribute : attributes) {
            sum += player.attribute(attribute);
        }
        return sum / attributes.length;
    }

    public String getLabel() {
        return Messages.text("CombineTest." + name(), label);
    }

    public String getUnit() {
        return Messages.text("CombineTest." + name() + ".unit", unit);
    }

    public int getDecimals() {
        return decimals;
    }

    public boolean isLowerIsBetter() {
        return lowerIsBetter;
    }
}
