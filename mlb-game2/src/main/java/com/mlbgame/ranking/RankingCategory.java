package com.mlbgame.ranking;

import com.mlbgame.common.Messages;
import java.util.function.ToDoubleFunction;
import org.springframework.data.domain.Sort;

/** 랭킹 카테고리 — 정렬 칼럼, 방향, 자격 조건 */
public enum RankingCategory {
    EARNINGS("커리어 누적 연봉", "careerEarnings", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getCareerEarnings),
    HALL_OF_FAME("명예의 전당 점수", "hallOfFame", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getHallOfFame),
    AWARDS("수상 포인트", "awardPoints", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getAwardPoints),
    HITS("통산 안타", "mlbHits", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getMlbHits),
    HOME_RUNS("통산 홈런", "mlbHomeRuns", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getMlbHomeRuns),
    AVG("통산 타율", "mlbAvg", Sort.Direction.DESC, Qualifier.PLATE_APPEARANCES, RankingEntry::getMlbAvg),
    WINS("통산 승", "mlbWins", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getMlbWins),
    STRIKEOUTS("통산 탈삼진", "mlbStrikeouts", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getMlbStrikeouts),
    SAVES("통산 세이브", "mlbSaves", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getMlbSaves),
    ERA("통산 평균자책점", "mlbEra", Sort.Direction.ASC, Qualifier.INNINGS, RankingEntry::getMlbEra),
    COMBINE("컴바인 종합 점수", "combineScore", Sort.Direction.DESC, Qualifier.NONE, RankingEntry::getCombineScore);

    /** 통산 타율 자격: 1,500타석 / 통산 ERA 자격: 450이닝 */
    public static final int MIN_PLATE_APPEARANCES = 1500;
    public static final int MIN_OUTS = 1350;

    public enum Qualifier {NONE, PLATE_APPEARANCES, INNINGS}

    private final String label;
    private final String property;
    private final Sort.Direction direction;
    private final Qualifier qualifier;
    private final ToDoubleFunction<RankingEntry> value;

    RankingCategory(String label, String property, Sort.Direction direction, Qualifier qualifier,
                    ToDoubleFunction<RankingEntry> value) {
        this.label = label;
        this.property = property;
        this.direction = direction;
        this.qualifier = qualifier;
        this.value = value;
    }

    public Sort sort() {
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "retiredAt"));
    }

    public double valueOf(RankingEntry entry) {
        return value.applyAsDouble(entry);
    }

    public String getLabel() {
        return Messages.text("RankingCategory." + name(), label);
    }

    public Qualifier getQualifier() {
        return qualifier;
    }

    /** 0이면 순위에서 제외할 누적 카테고리 (예: 홈런 0인 투수) */
    public boolean excludesZero() {
        return this != EARNINGS && this != COMBINE && this != HALL_OF_FAME && qualifier == Qualifier.NONE;
    }
}
