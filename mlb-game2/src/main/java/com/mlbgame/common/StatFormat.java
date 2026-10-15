package com.mlbgame.common;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import java.text.DecimalFormat;
import java.text.NumberFormat;
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
        return suffix.isEmpty() ? team.getNameKo() : team.getNameKo() + " " + suffix;
    }

    /** 하이라이트 문장 앞에 붙일 아이콘 — 문장의 핵심 키워드로 고른다 (앞에 있는 규칙이 우선) */
    public String icon(String highlight) {
        if (highlight == null) {
            return "•";
        }
        if (highlight.contains("부상")) return "🚑";
        if (highlight.contains("블론")) return "💥";
        if (highlight.contains("끝내기")) return "🎉";
        if (highlight.contains("홈런")) return "💣";
        if (highlight.contains("탈삼진 쇼")) return "🔥";
        if (highlight.contains("세이브") || highlight.contains("구원승")) return "🔒";
        if (highlight.contains("역전")) return "🔄";
        if (highlight.contains("맹타") || highlight.contains("타점")) return "🏏";
        if (highlight.contains("보살") || highlight.contains("도루 저지")) return "🧤";
        if (highlight.contains("이닝")) return "⚾";
        return "•";
    }

    /** 이닝별 득점 표 칸: 진행하지 않은 말 공격은 "X" */
    public String inningCell(List<Integer> line, int index) {
        return index < line.size() ? String.valueOf(line.get(index)) : "X";
    }
}
