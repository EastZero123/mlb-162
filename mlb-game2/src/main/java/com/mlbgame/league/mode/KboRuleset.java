package com.mlbgame.league.mode;

import com.mlbgame.award.AwardCatalog;
import com.mlbgame.combine.DraftRules;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.Team;
import com.mlbgame.league.world.PayrollRules;
import com.mlbgame.postseason.KboPostseasonFormat;
import com.mlbgame.postseason.PostseasonFormat;
import com.mlbgame.season.promotion.PromotionRules;
import com.mlbgame.season.schedule.KboScheduleGenerator;
import com.mlbgame.season.schedule.ScheduledSeries;
import java.util.List;

/**
 * KBO 규칙 (docs/kbo-mode.md 3장). 수치는 근사이며 K4 밸런스 검증으로 확정한다.
 *
 * <pre>
 * 리그      10개 구단 단일 리그, 퓨처스 → 1군
 * 일정      54시리즈 = 3연전 36 + 2연전 18 = 144경기
 * 승강      3시리즈(약 1~1.5주)마다 1군 등록·말소 판정 — 말소되면 다음 판정까지 재등록 없음 (실제 "말소 후 10일")
 * 포스트시즌  5팀 계단식 (와일드카드 결정전 → 준PO → PO → 한국시리즈)
 * 경제      원화, 최저 연봉 3,000만원, 1군 1년 차부터 연봉 협상, FA 고졸 8년·대졸 7년
 * 서비스 연수 1군 등록일수 145일 ≈ 1군 42시리즈 = 1년
 * </pre>
 */
public final class KboRuleset implements LeagueRuleset {

    static final int ALL_STAR_AFTER = 27;
    static final int SERIES_PER_SERVICE_YEAR = 42;

    /** 판정 주기가 짧아 표본 기준도 줄였다. 성적 기준값은 KBO 리그 평균(타고투저)에 맞춤 */
    static final PromotionRules.Params PROMOTION = new PromotionRules.Params(
            3, 2, 4, 4, -8,
            0.750, 0.050, 25,
            4.50, 0.60, 24,
            4.20, 0.80, 6,
            PromotionRules.LEGEND_HALL_OF_FAME_SCORE);

    @Override
    public LeagueMode mode() {
        return LeagueMode.KBO;
    }

    @Override
    public int seriesPerSeason() {
        return KboScheduleGenerator.SERIES_PER_SEASON;
    }

    @Override
    public int gamesInSeries(int seriesNo) {
        return KboScheduleGenerator.gamesInSeries(seriesNo);
    }

    @Override
    public List<ScheduledSeries> schedule(long seed, Team myTeam, List<Team> teams) {
        return KboScheduleGenerator.generate(seed, myTeam, teams);
    }

    @Override
    public List<String> leagues() {
        return List.of("KBO");
    }

    /** 지구 없음 — 순위표는 한 표 */
    @Override
    public List<String> divisions() {
        return List.of();
    }

    @Override
    public PostseasonFormat postseason() {
        return KboPostseasonFormat.INSTANCE;
    }

    @Override
    public PromotionRules.Params promotion() {
        return PROMOTION;
    }

    @Override
    public LeagueTerms terms() {
        return LeagueTerms.KBO;
    }

    @Override
    public AwardCatalog awards() {
        return AwardCatalog.KBO;
    }

    @Override
    public int allStarAfterSeries() {
        return ALL_STAR_AFTER;
    }

    @Override
    public PayrollRules payroll() {
        return PayrollRules.KBO;
    }

    @Override
    public int seriesPerServiceYear() {
        return SERIES_PER_SERVICE_YEAR;
    }

    @Override
    public DraftRules draft() {
        return DraftRules.KBO;
    }
}
