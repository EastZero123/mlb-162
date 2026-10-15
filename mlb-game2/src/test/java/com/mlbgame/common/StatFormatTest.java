package com.mlbgame.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatFormatTest {

    private final StatFormat fmt = new StatFormat();

    @Test
    void 하이라이트_키워드에_맞는_아이콘을_고른다() {
        assertThat(fmt.icon("2차전 9회말 끝내기 솔로 홈런!")).isEqualTo("🎉");
        assertThat(fmt.icon("1차전 7회초 역전 투런 홈런!")).isEqualTo("💣");
        assertThat(fmt.icon("3차전 슬라이더을(를) 앞세워 9탈삼진 쇼")).isEqualTo("🔥");
        assertThat(fmt.icon("2차전 블론세이브 (1이닝 2실점 0탈삼진)")).isEqualTo("💥");
        assertThat(fmt.icon("1차전 2점 차 리드를 지켜내고 세이브")).isEqualTo("🔒");
        assertThat(fmt.icon("2차전 부상 발생: 경미 (1시리즈 결장)")).isEqualTo("🚑");
        assertThat(fmt.icon("1차전 7이닝 1실점 8탈삼진 퀄리티스타트")).isEqualTo("⚾");
        assertThat(fmt.icon("아무 키워드 없음")).isEqualTo("•");
        assertThat(fmt.icon(null)).isEqualTo("•");
    }

    @Test
    void 기본_형식() {
        assertThat(fmt.rate(0.2871)).isEqualTo(".287");
        assertThat(fmt.money(1234567)).isEqualTo("$1,234,567");
    }
}
