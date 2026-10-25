package com.mlbgame.league.mode;

import com.mlbgame.award.AwardCatalog;
import com.mlbgame.combine.DraftRules;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.Team;
import com.mlbgame.league.world.PayrollRules;
import com.mlbgame.postseason.PostseasonFormat;
import com.mlbgame.season.promotion.PromotionRules;
import com.mlbgame.season.schedule.ScheduledSeries;
import java.util.List;

/**
 * 모드별 리그 규칙 묶음. 서비스는 MLB 값을 직접 쓰지 않고 선수·월드·레벨의 모드로 이 규칙셋을 찾아 묻는다.
 * 설계: docs/kbo-mode.md 2장
 */
public interface LeagueRuleset {

    LeagueMode mode();

    // ------------------------------------------------------------ 레벨

    /** 하위 → 최상위 */
    default List<LeagueLevel> levels() {
        return LeagueLevel.of(mode());
    }

    default LeagueLevel topLevel() {
        List<LeagueLevel> levels = levels();
        return levels.get(levels.size() - 1);
    }

    /** 한 단계 위 (최상위면 null) */
    default LeagueLevel above(LeagueLevel level) {
        List<LeagueLevel> levels = levels();
        int i = levels.indexOf(level);
        return i >= 0 && i + 1 < levels.size() ? levels.get(i + 1) : null;
    }

    /** 한 단계 아래 (최하위면 null) */
    default LeagueLevel below(LeagueLevel level) {
        int i = levels().indexOf(level);
        return i > 0 ? levels().get(i - 1) : null;
    }

    // ------------------------------------------------------------ 시즌·일정

    int seriesPerSeason();

    /** seriesNo 번째 시리즈의 경기 수 (리그 전체가 같은 편성) */
    int gamesInSeries(int seriesNo);

    /** from ~ to 번 시리즈의 경기 수 합 */
    default int gamesBetween(int fromSeries, int toSeries) {
        int games = 0;
        for (int s = fromSeries; s <= toSeries; s++) {
            games += gamesInSeries(s);
        }
        return games;
    }

    default int seasonGames() {
        return gamesBetween(1, seriesPerSeason());
    }

    /** 한 달(월간 시상 구간)의 시리즈 수 */
    default int seriesPerMonth() {
        return ScheduledSeries.SERIES_PER_MONTH;
    }

    /** 시즌 n번째 달(1부터)의 경기 수 */
    default int gamesInMonth(int monthIndex) {
        int last = monthIndex * seriesPerMonth();
        return gamesBetween(last - seriesPerMonth() + 1, last);
    }

    /** 내 구단의 시즌 일정 (같은 시드면 같은 일정) */
    List<ScheduledSeries> schedule(long seed, Team myTeam, List<Team> teams);

    // ------------------------------------------------------------ 순위·포스트시즌

    /** 순위표 리그 구분 (MLB: AL·NL) */
    List<String> leagues();

    /** 순위표 지구 구분 (없으면 빈 목록) */
    List<String> divisions();

    PostseasonFormat postseason();

    // ------------------------------------------------------------ 승강·시상

    PromotionRules.Params promotion();

    /** 화면 용어 (메이저리그·1군 등) */
    LeagueTerms terms();

    /** 시상 목록 (판정 자리마다 어떤 상을 주는지) */
    AwardCatalog awards();

    /** 이 시리즈를 마치면 올스타 선정 */
    int allStarAfterSeries();

    // ------------------------------------------------------------ 경제·계약·드래프트·트레이드

    PayrollRules payroll();

    /** 최상위 레벨 시리즈 몇 개가 서비스 1년인지 */
    int seriesPerServiceYear();

    DraftRules draft();

    /** 명예의 전당 점수의 누적 기록 보정 = 162 / 시즌 경기 수 (MLB 1.0) */
    default double hallOfFameStatScale() {
        return 162.0 / seasonGames();
    }

    /** 트레이드 이벤트가 일어나는 레벨: 최상위와 그 바로 아래 */
    default boolean tradeEligible(LeagueLevel level) {
        return level.isTop() || level == below(topLevel());
    }
}
