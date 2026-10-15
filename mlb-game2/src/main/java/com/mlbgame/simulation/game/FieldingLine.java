package com.mlbgame.simulation.game;

/** 수비 기록 (경기·시리즈·시즌 누적에 공용) */
public class FieldingLine {

    /** 내 쪽으로 온 인플레이 타구 수 */
    public int chances;
    /** 안타가 될 타구를 수비로 막은 횟수 */
    public int hitsSaved;
    /** 아웃이 될 타구를 놓쳐 안타를 내준 횟수 (수비 범위 부족) */
    public int hitsAllowed;
    public int errors;
    /** 보살 (외야 송구로 주자 아웃, 내야 병살 연결) */
    public int assists;
    /** 포수: 상대 도루 시도 / 저지 */
    public int stolenBasesAgainst;
    public int caughtStealing;

    public void add(FieldingLine o) {
        chances += o.chances;
        hitsSaved += o.hitsSaved;
        hitsAllowed += o.hitsAllowed;
        errors += o.errors;
        assists += o.assists;
        stolenBasesAgainst += o.stolenBasesAgainst;
        caughtStealing += o.caughtStealing;
    }

    /** 수비로 아낀 실점 추정 (막은 안타 0.75점, 내준 안타·실책 −0.75점, 보살·도루 저지 0.5점) */
    public double getRunsSaved() {
        return (hitsSaved - hitsAllowed - errors) * 0.75 + (assists + caughtStealing) * 0.5;
    }

    public double getCaughtStealingRate() {
        int attempts = stolenBasesAgainst + caughtStealing;
        return attempts == 0 ? 0 : (double) caughtStealing / attempts;
    }
}
