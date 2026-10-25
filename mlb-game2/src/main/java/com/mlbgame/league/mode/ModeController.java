package com.mlbgame.league.mode;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.league.LeagueMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 상단 MLB | KBO 탭 (언어 전환처럼 쿠키에 기억). 로그인 전에도 쓸 수 있다.
 * 돌아갈 곳은 고정 — 외부 주소로 보내는 redirect 파라미터는 받지 않는다.
 */
@Controller
public class ModeController {

    @GetMapping("/mode/{mode}")
    public String switchMode(@AuthenticationPrincipal AuthUser authUser, @PathVariable String mode,
                             HttpServletRequest request, HttpServletResponse response) {
        LeagueMode selected = ModeCookie.parse(mode);
        ModeCookie.write(request, response, selected);
        return authUser != null ? "redirect:/dashboard" : "redirect:/rankings";
    }
}
