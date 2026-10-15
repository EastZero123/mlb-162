package com.mlbgame.combine;

/**
 * 컴바인 종합 등급과 등급별 드래프트 라운드 범위.
 * 기준 점수는 대졸(능력치 40) / 고졸(능력치 30 + 나이 프리미엄 10) 모두 평균 43 안팎이 되도록 잡았다.
 */
public enum CombineGrade {
    S(52, 1, 1, "드래프트 최대어. 1라운드 지명이 확실시된다."),
    A(47, 2, 3, "상위 라운드 지명이 유력한 유망주."),
    B(41, 4, 8, "프로에서 충분히 통할 자질을 보여줬다."),
    C(36, 9, 14, "가능성은 보였지만 보완할 점이 많다."),
    D(Integer.MIN_VALUE, 15, 20, "아쉬운 하루. 프로에서 증명해야 한다.");

    /** 등급 내 위치 계산용 상한 (S 등급의 상한) */
    static final int TOP_SCORE = 60;

    private final int minScore;
    private final int firstRound;
    private final int lastRound;
    private final String comment;

    CombineGrade(int minScore, int firstRound, int lastRound, String comment) {
        this.minScore = minScore;
        this.firstRound = firstRound;
        this.lastRound = lastRound;
        this.comment = comment;
    }

    public static CombineGrade of(double totalScore) {
        for (CombineGrade grade : values()) {
            if (totalScore >= grade.minScore) {
                return grade;
            }
        }
        return D;
    }

    /** 등급 범위 안에서 점수가 높을수록 앞 라운드 */
    public int roundFor(double totalScore) {
        if (firstRound == lastRound) {
            return firstRound;
        }
        double lower = this == D ? 30 : minScore;
        double upper = ordinal() == 0 ? TOP_SCORE : values()[ordinal() - 1].minScore;
        double t = Math.max(0, Math.min(0.999, (totalScore - lower) / (upper - lower)));
        int span = lastRound - firstRound + 1;
        return Math.min(lastRound, firstRound + (int) Math.floor((1 - t) * span));
    }

    public int getMinScore() {
        return minScore;
    }

    public String getComment() {
        return comment;
    }
}
