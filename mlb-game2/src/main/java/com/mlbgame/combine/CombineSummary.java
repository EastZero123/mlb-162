package com.mlbgame.combine;

import com.mlbgame.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 컴바인 종합 평가 (선수당 1건) */
@Entity
@Table(name = "combine_summaries")
public class CombineSummary extends BaseTimeEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "day_condition", nullable = false, length = 10)
    private DayCondition dayCondition;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "interview_answer", nullable = false, length = 20)
    private InterviewAnswer interviewAnswer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scout_preference", nullable = false, length = 20)
    private InterviewAnswer scoutPreference;

    @Column(name = "interview_bonus", nullable = false)
    private int interviewBonus;

    @Column(name = "age_bonus", nullable = false)
    private int ageBonus;

    @Column(name = "total_score", nullable = false)
    private double totalScore;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 2)
    private CombineGrade grade;

    @Column(name = "scout_comment", nullable = false)
    private String scoutComment;

    protected CombineSummary() {
    }

    public CombineSummary(Long playerId, DayCondition dayCondition, InterviewAnswer interviewAnswer,
                          InterviewAnswer scoutPreference, int interviewBonus, int ageBonus,
                          double totalScore, CombineGrade grade, String scoutComment) {
        this.playerId = playerId;
        this.dayCondition = dayCondition;
        this.interviewAnswer = interviewAnswer;
        this.scoutPreference = scoutPreference;
        this.interviewBonus = interviewBonus;
        this.ageBonus = ageBonus;
        this.totalScore = totalScore;
        this.grade = grade;
        this.scoutComment = scoutComment;
    }

    public boolean isInterviewMatched() {
        return interviewAnswer == scoutPreference;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public DayCondition getDayCondition() {
        return dayCondition;
    }

    public InterviewAnswer getInterviewAnswer() {
        return interviewAnswer;
    }

    public InterviewAnswer getScoutPreference() {
        return scoutPreference;
    }

    public int getInterviewBonus() {
        return interviewBonus;
    }

    public int getAgeBonus() {
        return ageBonus;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public CombineGrade getGrade() {
        return grade;
    }

    public String getScoutComment() {
        return scoutComment;
    }
}
