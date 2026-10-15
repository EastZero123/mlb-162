package com.mlbgame.player;

import java.util.List;

/**
 * 선수 능력치. 타자·투수 각각 11개를 사용하며 OVR 은 11개의 단순 평균이다.
 * 모든 능력치는 시뮬레이션 결과(기록 지표)에 직접 연결된다 — 결과에 영향 없는 "함정 능력치"를 두지 않는다.
 * 설계: docs/player-attributes.md
 */
public enum Attribute {
    // 타자 (포수도 같은 11개를 쓰고, 수비·송구를 포수 방식으로 해석)
    CONTACT("컨택", "인플레이 타구가 안타가 될 확률 → 타율"),
    AVOID_K("삼진 회피", "삼진 확률↓ → 삼진 수, K%"),
    EYE("선구안", "볼넷 확률↑ → 볼넷, 출루율"),
    POWER("파워", "홈런 확률 → 홈런"),
    GAP_POWER("갭 파워", "2루타 확률 → 2루타, 장타율"),
    SPEED("주력", "내야안타·3루타, 병살 회피"),
    STEALING("도루", "도루 시도·성공률"),
    FIELDING("수비", "수비로 막은 안타, 실책↓ (포수: 블로킹·프레이밍)"),
    ARM("송구", "보살, 주자 진루 억제 (포수: 도루 저지)"),
    CLUTCH("클러치", "득점권 타석 보정 → 득점권 타율, 타점"),

    // 투수 (선발 SP / 마무리 CP 공통 11개, 체력·투구 효율·회복력은 보직별로 해석)
    VELOCITY("구속", "피안타율, 강한 타구 억제 (130~165km/h 표시)"),
    CONTROL("제구", "볼넷 확률↓ → BB/9"),
    PUT_AWAY("결정구", "2스트라이크 이후 삼진 확률 → K/9"),
    MOVEMENT("무브먼트", "피홈런 확률↓ → HR/9"),
    GROUNDBALL("땅볼 유도", "땅볼 비율↑ → 병살 유도, 피장타↓"),
    STAMINA("체력", "SP: 투구 수 한도·이닝 / CP: 연투 시 구위 유지, 4아웃 세이브"),
    EFFICIENCY("투구 효율", "타자당 투구 수↓ → SP: 긴 이닝 / CP: 다음 날 등판 가능"),
    COMPOSURE("위기 관리", "득점권 상황 보정 → 득점권 피안타율, 잔루율"),
    PLATOON("좌우 대응", "반대손 타자 상대 불리함↓ → 좌·우타자별 피안타율"),
    RECOVERY("회복력", "등판 후 피로 회복 → SP: 로테이션 유지 / CP: 연투 제한 완화"),

    // 공통
    DURABILITY("내구성", "부상·피로 누적↓ → 출장 경기 수");

    public static final int MIN = 1;
    public static final int MAX = 100;
    public static final int COUNT_PER_POSITION = 11;

    private static final List<Attribute> HITTER = List.of(
            CONTACT, AVOID_K, EYE, POWER, GAP_POWER, SPEED, STEALING, FIELDING, ARM, CLUTCH, DURABILITY);

    private static final List<Attribute> PITCHER = List.of(
            VELOCITY, CONTROL, PUT_AWAY, MOVEMENT, GROUNDBALL, STAMINA, EFFICIENCY, COMPOSURE, PLATOON,
            RECOVERY, DURABILITY);

    private final String label;
    private final String description;

    Attribute(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** 포지션이 사용하는 능력치 11개 (화면 표시 순서) */
    public static List<Attribute> forPosition(Position position) {
        return position.isPitcher() ? PITCHER : HITTER;
    }
}
