package com.mlbgame.award;

/**
 * 모드별 시상 목록: {@link AwardService} 의 판정 자리(월간·올스타·시즌)마다 어떤 상을 주는지. null 이면 그 자리는 시상 없음.
 *
 * <p>KBO 는 사이영상이 없으므로 시즌 최고 선발 자리는 투수 골든글러브, 최고 타자(포지션별) 자리는 타자 골든글러브,
 * 수비 자리는 KBO 수비상으로 바꾸고, 대신 개인 타이틀({@code titles})을 따로 준다 (docs/kbo-mode.md 3.5).
 *
 * @param titles 정규시즌 개인 타이틀(타율·홈런·타점·도루·다승·평균자책점·탈삼진·세이브 1위)을 주는가
 * @param scoring 시상 점수 기준값 (리그 평균 근처)
 * @param pitcherMvp 압도적인 선발(최고 선발 + 16승 + ERA 2.50 이하)에게도 정규시즌 MVP 를 주는가 (KBO: 2023 페디 등)
 */
public record AwardCatalog(
        AwardType playerOfMonth,
        AwardType pitcherOfMonth,
        AwardType rookieOfMonth,
        AwardType relieverOfMonth,
        AwardType minorPlayerOfMonth,
        AwardType allStar,
        AwardType allStarMvp,
        AwardType minorAllStar,
        AwardType mvp,
        AwardType bestStarter,
        AwardType rookieOfYear,
        AwardType bestHitterAtPosition,
        AwardType bestDefender,
        AwardType bestReliever,
        AwardType minorPlayerOfYear,
        boolean titles,
        AwardRules.Scoring scoring,
        boolean pitcherMvp
) {

    public static final AwardCatalog MLB = new AwardCatalog(
            AwardType.PLAYER_OF_MONTH, AwardType.PITCHER_OF_MONTH, AwardType.ROOKIE_OF_MONTH,
            AwardType.RELIEVER_OF_MONTH, AwardType.MINOR_PLAYER_OF_MONTH,
            AwardType.ALL_STAR, AwardType.ALL_STAR_MVP, AwardType.MINOR_ALL_STAR,
            AwardType.MVP, AwardType.CY_YOUNG, AwardType.ROOKIE_OF_YEAR, AwardType.SILVER_SLUGGER,
            AwardType.GOLD_GLOVE, AwardType.RELIEVER_OF_YEAR, AwardType.MINOR_PLAYER_OF_YEAR,
            false, AwardRules.Scoring.MLB, false);

    /** KBO: 월간 MVP 는 타자·투수 구분 없이 하나, 이달의 루키 없음, 올해의 구원투수 대신 세이브왕 타이틀 */
    public static final AwardCatalog KBO = new AwardCatalog(
            AwardType.KBO_MONTHLY_MVP, AwardType.KBO_MONTHLY_MVP, null,
            AwardType.KBO_MONTHLY_MVP, AwardType.FUTURES_PLAYER_OF_MONTH,
            AwardType.KBO_ALL_STAR, AwardType.KBO_ALL_STAR_MVP, AwardType.FUTURES_ALL_STAR,
            AwardType.KBO_MVP, AwardType.KBO_GOLDEN_GLOVE, AwardType.KBO_ROOKIE_OF_YEAR, AwardType.KBO_GOLDEN_GLOVE,
            AwardType.KBO_DEFENSE_AWARD, null, AwardType.FUTURES_PLAYER_OF_YEAR,
            true, AwardRules.Scoring.KBO, true);
}
