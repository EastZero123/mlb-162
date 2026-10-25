package com.mlbgame.combine;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.player.Education;

/**
 * 모드별 드래프트 수치.
 *
 * @param teamsPerRound     라운드당 지명 수 (= 구단 수)
 * @param rounds            드래프트 라운드 수 (컴바인 등급은 20라운드 기준 → 이 수로 줄인다)
 * @param firstPickBonus    전체 1순위 계약금
 * @param bonusDecay        지명 순번마다 계약금 배율
 * @param minimumBonus      최저 계약금
 * @param bonusUnit         계약금 반올림 단위
 * @param collegeLevel      대졸 상위 라운드 지명자의 시작 레벨
 * @param collegeMaxRound   collegeLevel 에서 시작하는 대졸 지명 라운드 상한
 * @param defaultLevel      그 외(고졸 포함) 시작 레벨
 */
public record DraftRules(int teamsPerRound, int rounds, double firstPickBonus, double bonusDecay, long minimumBonus,
                         long bonusUnit, LeagueLevel collegeLevel, int collegeMaxRound, LeagueLevel defaultLevel) {

    /** 컴바인 등급이 정하는 라운드 범위 (S 1R ~ D 20R) */
    static final int GRADE_ROUNDS = 20;

    /** 30개 구단·20라운드, 전체 1순위 950만 달러에서 순번마다 4% 감소(최저 15만 달러), 대졸 1~10R 싱글A · 그 외 루키리그 */
    public static final DraftRules MLB = new DraftRules(30, 20, 9_500_000, 0.96, 150_000L, 10_000L,
            LeagueLevel.A, 10, LeagueLevel.ROOKIE);

    /** 10개 구단·11라운드, 전체 1순위 5억에서 순번마다 4% 감소(최저 3,000만원), 전원 퓨처스 시작 */
    public static final DraftRules KBO = new DraftRules(10, 11, 500_000_000, 0.96, 30_000_000L, 1_000_000L,
            LeagueLevel.FUTURES, 0, LeagueLevel.FUTURES);

    /** 컴바인 등급의 라운드(1~20)를 이 드래프트의 라운드로 (MLB 는 그대로) */
    public int round(int gradeRound) {
        return Math.max(1, (int) Math.ceil(gradeRound * (double) rounds / GRADE_ROUNDS));
    }

    public LeagueLevel startLevel(Education education, int round) {
        return education == Education.COLLEGE && round <= collegeMaxRound ? collegeLevel : defaultLevel;
    }

    public long signingBonus(int overallPick) {
        double bonus = firstPickBonus * Math.pow(bonusDecay, overallPick - 1);
        return Math.max(minimumBonus, Math.round(bonus / bonusUnit) * bonusUnit);
    }
}
