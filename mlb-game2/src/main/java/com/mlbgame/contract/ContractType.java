package com.mlbgame.contract;

public enum ContractType {
    /** 메이저 계약 없음 (마이너 레벨 연봉) */
    NONE("마이너 계약", false),
    /** 서비스 0~2년 최저 연봉 구간 */
    PRE_ARB("최저 연봉", false),
    /** 연봉조정 1년 계약 */
    ARBITRATION("연봉조정", true),
    /** 구단과의 다년 연장 계약 */
    EXTENSION("연장 계약", true),
    /** FA 계약 */
    FREE_AGENT("FA 계약", true);

    private final String label;
    /** 보장 계약: 마이너로 내려가도 계약 연봉을 그대로 받는다 */
    private final boolean guaranteed;

    ContractType(String label, boolean guaranteed) {
        this.label = label;
        this.guaranteed = guaranteed;
    }

    public String getLabel() {
        return label;
    }

    public boolean isGuaranteed() {
        return guaranteed;
    }
}
