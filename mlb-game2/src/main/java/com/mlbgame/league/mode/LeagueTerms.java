package com.mlbgame.league.mode;

import com.mlbgame.common.Messages;

/**
 * 화면 문구에 쓰는 모드별 용어 (템플릿에서 {@code ${terms.top}} 처럼, {@code ModeAdvice} 가 넣는다).
 * 한국어는 여기 그대로, 영어는 messages_en 의 {@code terms.mlb.top} 같은 키에서 찾는다 ({@link Messages#text}).
 *
 * @param key          메시지 키 접두사 (terms.mlb / terms.kbo)
 * @param top          최상위 레벨 (메이저리그 / 1군)
 * @param topShort     짧게 (메이저 / 1군)
 * @param minor        그 아래 (마이너리그 / 퓨처스리그)
 * @param minorShort   짧게 (마이너 / 퓨처스)
 * @param finals       우승 결정 시리즈 (월드시리즈 / 한국시리즈)
 * @param arbitration  서비스 연차 연봉 결정 방식 (연봉조정 / 연봉 협상)
 * @param contractNote 계약 화면 안내 문구
 */
public record LeagueTerms(String key, String top, String topShort, String minor, String minorShort, String finals,
                          String arbitration, String contractNote) {

    public static final LeagueTerms MLB = new LeagueTerms("terms.mlb", "메이저리그", "메이저", "마이너리그", "마이너",
            "월드시리즈", "연봉조정",
            "서비스 3~5년은 연봉조정, 6년 이후 계약이 끝나면 FA가 됩니다. 메이저에서 46시리즈를 뛰면 서비스 1년이에요.");

    public static final LeagueTerms KBO = new LeagueTerms("terms.kbo", "1군", "1군", "퓨처스리그", "퓨처스",
            "한국시리즈", "연봉 협상",
            "1군 1년 차부터 매년 연봉 협상, 고졸 8년·대졸 7년이 지나 계약이 끝나면 FA가 됩니다. 1군에서 42시리즈를 뛰면 서비스 1년이에요.");

    @Override
    public String top() {
        return Messages.text(key + ".top", top);
    }

    @Override
    public String topShort() {
        return Messages.text(key + ".topShort", topShort);
    }

    @Override
    public String minor() {
        return Messages.text(key + ".minor", minor);
    }

    @Override
    public String minorShort() {
        return Messages.text(key + ".minorShort", minorShort);
    }

    @Override
    public String finals() {
        return Messages.text(key + ".finals", finals);
    }

    @Override
    public String arbitration() {
        return Messages.text(key + ".arbitration", arbitration);
    }

    @Override
    public String contractNote() {
        return Messages.text(key + ".contractNote", contractNote);
    }
}
