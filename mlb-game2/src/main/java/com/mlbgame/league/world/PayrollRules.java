package com.mlbgame.league.world;

import com.mlbgame.common.Messages;
import com.mlbgame.league.LeagueLevel;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 모드별 연봉·예산 수치 (통화 단위 포함). {@link Payroll} 의 static 메서드는 {@link #MLB} 를 쓴다.
 *
 * @param minimumSalary          최상위 레벨 최저 연봉
 * @param luxuryTaxThreshold     사치세(경쟁균형세) 기준선
 * @param marketBase             OVR 65 시장가
 * @param marketGrowth           OVR 1당 시장가 지수 증가율
 * @param salaryUnit             연봉 반올림 단위
 * @param budgetUnit             구단 예산 반올림 단위
 * @param arbitrationStart       연봉조정(KBO 연봉 협상) 시작 서비스 연수
 * @param freeAgencyService      FA 자격 서비스 연수 (고졸 기준)
 * @param collegeFreeAgencyCut   대졸이면 FA 자격 연수에서 빼는 값 (KBO 고졸 8 / 대졸 7)
 * @param arbitrationShare       연봉조정 연차별 시장가 대비 비율 (모자라면 마지막 값)
 * @param levelSalaries          최상위 아래 레벨의 시즌 연봉
 * @param baseBudgets            재정 규모별 기본 예산
 * @param expensiveSalary        트레이드 확률을 올리는 고액 연봉 기준
 * @param budgetRoomScale        트레이드 행선지 가중치에서 예산 여유 1점에 해당하는 금액
 * @param currency               화면 표시 통화
 */
public record PayrollRules(long minimumSalary, long luxuryTaxThreshold, double marketBase, double marketGrowth,
                           long salaryUnit, long budgetUnit, int arbitrationStart, int freeAgencyService,
                           int collegeFreeAgencyCut, double[] arbitrationShare, Map<LeagueLevel, Long> levelSalaries,
                           Map<Market, Long> baseBudgets, long expensiveSalary, double budgetRoomScale,
                           Currency currency) {

    public enum Currency {USD, KRW}

    public static final PayrollRules MLB = new PayrollRules(
            760_000L, 240_000_000L, 8_000_000, 0.075, 10_000L, 1_000_000L, 3, 6, 0,
            new double[]{0.4, 0.6, 0.8},
            Map.of(LeagueLevel.ROOKIE, 20_000L, LeagueLevel.A, 25_000L, LeagueLevel.AA, 30_000L,
                    LeagueLevel.AAA, 40_000L),
            Map.of(Market.BIG, 230_000_000L, Market.MID, 160_000_000L, Market.SMALL, 100_000_000L),
            15_000_000L, 100_000_000.0, Currency.USD);

    /**
     * KBO (원). 평균 선수(OVR 65) 시장가 2억, 스타 격차는 MLB 보다 커서 OVR 1당 10% (OVR 85 약 15억, 90 약 24억, 95 약 40억).
     * K4 커리어 검증에서 0.075(MLB 와 같은 기울기)로는 명예의 전당급 최고 연봉이 15억 안팎으로 실제(연 20~38억)의 절반이라 올렸고,
     * 늘어난 스타 연봉만큼 구단 예산·경쟁균형세 기준선·트레이드 고액 연봉 기준도 약 30% 올렸다.
     * 최저 연봉 3,000만원, 1군 1년 차부터 매년 연봉 협상, FA 고졸 8년·대졸 7년. 수치는 K4 밸런스에서 조정.
     */
    public static final PayrollRules KBO = new PayrollRules(
            30_000_000L, 7_800_000_000L, 200_000_000, 0.10, 1_000_000L, 100_000_000L, 1, 8, 1,
            new double[]{0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85},
            Map.of(LeagueLevel.FUTURES, 30_000_000L),
            Map.of(Market.BIG, 7_500_000_000L, Market.MID, 5_200_000_000L, Market.SMALL, 3_300_000_000L),
            600_000_000L, 3_200_000_000.0, Currency.KRW);

    /** 시장가 = marketBase × e^(marketGrowth × (OVR − 65)), 연봉 단위 반올림, 최저 연봉 이상 */
    public long marketValue(double overall) {
        double value = marketBase * Math.exp(marketGrowth * (overall - 65));
        return Math.max(minimumSalary, round(value));
    }

    /** 서비스 연수 기준 연봉 (FA 계약은 별도로 시장가 × 변동) */
    public long salaryByService(int serviceYears, double overall) {
        if (serviceYears < arbitrationStart) {
            return minimumSalary;
        }
        if (serviceYears < freeAgencyService) {
            int index = Math.min(arbitrationShare.length - 1, serviceYears - arbitrationStart);
            return Math.max(minimumSalary, round(marketValue(overall) * arbitrationShare[index]));
        }
        return marketValue(overall);
    }

    /** 학력별 FA 자격 서비스 연수 */
    public int freeAgencyService(boolean college) {
        return freeAgencyService - (college ? collegeFreeAgencyCut : 0);
    }

    /** 레벨별 시즌 연봉 (최상위 레벨은 최저 연봉) */
    public long levelSalary(LeagueLevel level) {
        return level.isTop() ? minimumSalary : levelSalaries.get(level);
    }

    public long baseBudget(Market market) {
        return baseBudgets.get(market);
    }

    /** 연봉 단위 반올림 */
    public long round(double value) {
        return Math.round(value / salaryUnit) * salaryUnit;
    }

    /** 화면 표시 금액: MLB "$1,234,567", KBO "1억 5,000만원" (영어 화면 "₩150,000,000") */
    public String format(long value) {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.US);
        if (currency == Currency.USD) {
            return "$" + number.format(value);
        }
        if (Messages.english()) {
            return "₩" + number.format(value);                       // 영어 화면은 억·만 단위 대신 원화 기호
        }
        long eok = value / 100_000_000L;
        long man = value % 100_000_000L / 10_000L;
        if (eok > 0) {
            return eok + "억" + (man > 0 ? " " + number.format(man) + "만" : "") + "원";
        }
        return man > 0 ? number.format(man) + "만원" : number.format(value) + "원";
    }
}
