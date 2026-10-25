package com.mlbgame.league.mode;

import com.mlbgame.league.LeagueMode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * 지금 보고 있는 모드(상단 MLB | KBO 탭)를 쿠키에 기억한다. 로그인 전 공개 페이지(랭킹)에서도 쓴다.
 * 선수 화면은 쿠키가 아니라 선수에 저장된 모드를 따른다 ({@link ModeAdvice}).
 */
public final class ModeCookie {

    public static final String NAME = "league_mode";
    static final Duration MAX_AGE = Duration.ofDays(365);

    private ModeCookie() {
    }

    /** 쿠키가 없거나 알 수 없는 값이면 MLB */
    public static LeagueMode read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (NAME.equals(cookie.getName())) {
                    return parse(cookie.getValue());
                }
            }
        }
        return LeagueMode.MLB;
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, LeagueMode mode) {
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        ResponseCookie cookie = ResponseCookie.from(NAME, mode.name())
                .path(path)
                .maxAge(MAX_AGE)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** "kbo", "KBO" 모두 허용, 모르는 값은 MLB */
    public static LeagueMode parse(String value) {
        if (value != null) {
            for (LeagueMode mode : LeagueMode.values()) {
                if (mode.name().equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        return LeagueMode.MLB;
    }
}
