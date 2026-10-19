package com.mlbgame.common;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 템플릿용 표시 형식. Thymeleaf 에서 {@code ${@fmt.rate(line.average)}} 처럼 쓴다.
 */
@Component("fmt")
public class StatFormat {

    /** 타율·출루율 형식: 0.287 → ".287", 1.023 → "1.023" */
    public String rate(double value) {
        String text = new DecimalFormat("0.000").format(value);
        return text.startsWith("0.") ? text.substring(1) : text;
    }

    /** ERA·WHIP: 소수 둘째 자리 */
    public String two(double value) {
        return new DecimalFormat("0.00").format(value);
    }

    /** OVR 등: 소수 첫째 자리 */
    public String one(double value) {
        return new DecimalFormat("0.0").format(value);
    }

    /** 비율 → "12.3%" */
    public String pct(double value) {
        return new DecimalFormat("0.0").format(value * 100) + "%";
    }

    /** 리그 평균 100 기준 지수 (wRC+·OPS+·ERA+·FIP−): 반올림 정수, 값이 없으면 "-" */
    public String index(Double value) {
        return value == null ? "-" : String.valueOf(Math.round(value));
    }

    /** WAR: 소수 첫째 자리, 값이 없으면 "-" */
    public String war(Double value) {
        if (value == null) {
            return "-";
        }
        return Math.abs(value) < 0.05 ? "0.0" : new DecimalFormat("0.0").format(value);   // "-0.0" 방지
    }

    /** FIP 등 소수 둘째 자리, 값이 없으면 "-" */
    public String twoOrDash(Double value) {
        return value == null ? "-" : two(value);
    }

    /** $1,234,567 */
    public String money(long value) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    /** 레벨에 맞는 팀 표시명 — 마이너리그는 "디트로이트 타이거스 AA" */
    public String teamName(Team team, LeagueLevel level) {
        if (team == null) {
            return "-";
        }
        String suffix = level == null ? "" : level.getTeamSuffix();
        return suffix.isEmpty() ? team.getDisplayName() : team.getDisplayName() + " " + suffix;
    }

    /** 하이라이트 문장 앞에 붙일 아이콘 — 문장의 핵심 키워드로 고른다 (앞에 있는 규칙이 우선) */
    public String icon(String highlight) {
        if (highlight == null) {
            return "•";
        }
        if (has(highlight, "부상", "Injury")) return "🚑";
        if (has(highlight, "블론", "Blown save")) return "💥";
        if (has(highlight, "끝내기", "walk-off")) return "🎉";
        if (has(highlight, "홈런", "home run", "grand slam")) return "💣";
        if (has(highlight, "탈삼진 쇼", "strikeout show")) return "🔥";
        if (has(highlight, "세이브", "구원승", "save", "in relief")) return "🔒";
        if (has(highlight, "역전", "go-ahead")) return "🔄";
        if (has(highlight, "맹타", "타점", "big day", "RBI")) return "🏏";
        if (has(highlight, "보살", "도루 저지", "assist", "base stealer")) return "🧤";
        if (has(highlight, "이닝", " IP")) return "⚾";
        return "•";
    }

    /** 한국어·영어 하이라이트 키워드 중 하나라도 들어 있는지 */
    private static boolean has(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 월: "5월" / "May" */
    public String month(int month) {
        return Messages.month(month);
    }

    /** 월 (짧게): "5월" / "May", "Apr" */
    public String monthShort(int month) {
        return Messages.english() ? Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) : month + "월";
    }

    /** 이닝별 득점 표 칸: 진행하지 않은 말 공격은 "X" */
    public String inningCell(List<Integer> line, int index) {
        return index < line.size() ? String.valueOf(line.get(index)) : "X";
    }
}
