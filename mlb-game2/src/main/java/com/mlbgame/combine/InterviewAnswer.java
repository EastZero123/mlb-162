package com.mlbgame.combine;

/**
 * 스카우트 인터뷰 질문: "프로에서 이루고 싶은 목표는?"
 * 스카우트마다 선호하는 답이 랜덤으로 정해지고, 맞으면 가산점이 크다.
 */
public enum InterviewAnswer {
    TEAM("팀을 월드시리즈 우승으로 이끌고 싶습니다.", "팀 퍼스트"),
    LEGEND("명예의 전당에 오르는 선수가 되겠습니다.", "야망"),
    GROWTH("매일 어제보다 나은 선수가 되는 게 목표입니다.", "성실");

    public static final String QUESTION = "프로에서 이루고 싶은 목표는 무엇인가요?";
    public static final int MATCH_BONUS = 6;
    public static final int DEFAULT_BONUS = 2;

    private final String text;
    private final String trait;

    InterviewAnswer(String text, String trait) {
        this.text = text;
        this.trait = trait;
    }

    public String getText() {
        return text;
    }

    public String getTrait() {
        return trait;
    }
}
