package com.mlbgame.league.world;

public enum NpcStatus {
    /** 메이저 1군 */
    MLB,
    /** 1군에서 밀려남 — 경기 시뮬레이션 없음, 성장·노화는 계속, 재콜업 가능 */
    MINORS,
    /** 계약 만료 */
    FA,
    RETIRED
}
