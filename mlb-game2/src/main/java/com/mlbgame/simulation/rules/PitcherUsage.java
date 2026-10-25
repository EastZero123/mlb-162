package com.mlbgame.simulation.rules;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.simulation.game.UserPitcher.PitcherRole;

/**
 * 투수 등판 상태와 규칙. 경기마다 {@link #roleForGame} 으로 오늘 역할을 정하고,
 * 경기가 끝나면 {@link #afterGame} 으로 다음 상태를 만든다 (불변 객체 — 시드 재현에 유리).
 *
 * <p><b>선발(SP)</b>: 5인 로테이션 = 마지막 선발 후 5경기째에 등판.
 * 회복력이 40 미만이면 하루 밀리고(6경기), 레벨 평균보다 OVR 이 6 넘게 낮으면 6선발 취급(+1경기).
 *
 * <p><b>마무리(CP)</b>: 3일 연속 등판했으면 휴식, 전날 투구 수가 한도(25 + 회복력/5) 이상이면 휴식,
 * 4경기 이상 등판이 없으면 점검 등판. 연투 중에는 체력이 낮을수록 구위가 떨어진다.
 *
 * @param gamesSinceStart       마지막 선발 이후 지난 경기 수 (SP)
 * @param consecutiveDays       연속 등판 일수 (CP, 어제까지)
 * @param lastPitches           직전 경기 투구 수 (등판 안 했으면 0)
 * @param gamesSinceAppearance  마지막 등판 이후 지난 경기 수
 */
public record PitcherUsage(
        int gamesSinceStart,
        int consecutiveDays,
        int lastPitches,
        int gamesSinceAppearance
) {

    public static final int ROTATION = 5;
    public static final int LOW_RECOVERY = 40;
    public static final int MAX_CONSECUTIVE_DAYS = 2;   // 3일 연속 금지
    public static final int TUNE_UP_AFTER = 4;

    /** 시즌 시작 상태: 선발은 개막 첫 경기에 등판 가능 */
    public static PitcherUsage seasonStart() {
        return new PitcherUsage(ROTATION, 0, 0, TUNE_UP_AFTER);
    }

    public static PitcherUsage restAfterSeries(PitcherUsage usage) {
        // 시리즈 사이 이동일: 마무리 연투 기록은 끊긴다.
        // 선발 로테이션은 경기 기준으로 돈다 (이동일을 세지 않음 → 162경기 / 5 ≈ 시즌 32선발, MLB 수준)
        return new PitcherUsage(usage.gamesSinceStart, 0, 0, usage.gamesSinceAppearance + 1);
    }

    public PitcherRole roleForGame(boolean starter, int recovery, double overall, LeagueLevel level) {
        if (starter) {
            int rest = ROTATION;
            if (recovery < LOW_RECOVERY) rest++;
            if (overall - level.getRegularRating() < -6) rest++;
            return gamesSinceStart >= rest ? PitcherRole.STARTER : PitcherRole.NONE;
        }
        if (consecutiveDays >= MAX_CONSECUTIVE_DAYS) {
            return PitcherRole.NONE;
        }
        if (lastPitches >= closerPitchLimit(recovery)) {
            return PitcherRole.NONE;
        }
        return gamesSinceAppearance >= TUNE_UP_AFTER ? PitcherRole.CLOSER_TUNE_UP : PitcherRole.CLOSER;
    }

    /** 전날 이만큼 던졌으면 오늘 휴식 (회복력 65 → 38구) */
    public static int closerPitchLimit(int recovery) {
        return 25 + recovery / 5;
    }

    /** 마무리 연투 페널티 (능력치 단위): 연속 등판 일수 × 3 × (100 − 체력)/50 → 체력 65면 하루당 −2.1 */
    public double closerFatigueOffset(int stamina) {
        return -consecutiveDays * 3 * (100 - stamina) / 50.0;
    }

    public PitcherUsage afterGame(boolean appeared, boolean started, int pitches) {
        return new PitcherUsage(
                started ? 1 : gamesSinceStart + 1,
                appeared ? consecutiveDays + 1 : 0,
                appeared ? pitches : 0,
                appeared ? 1 : gamesSinceAppearance + 1);
    }
}
