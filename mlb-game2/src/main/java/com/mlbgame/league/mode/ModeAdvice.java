package com.mlbgame.league.mode;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.ranking.RankingEntry;
import com.mlbgame.ranking.RankingEntryRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 모든 화면에 현재 모드를 넣는다 ({@code currentMode}, {@code modes}, {@code currentModeAvailable}).
 *
 * <p>기본은 쿠키(상단 탭)이고, 선수 화면({@code /players/{playerId}/...})과 공개 프로필({@code /profiles/{playerId}})은
 * 그 선수의 모드를 따른다. 쿠키와 다르면 쿠키도 맞춰서 헤더 탭과 화면이 어긋나지 않게 한다.
 */
@ControllerAdvice
public class ModeAdvice {

    private final PlayerRepository playerRepository;
    private final RankingEntryRepository rankingEntryRepository;

    public ModeAdvice(PlayerRepository playerRepository, RankingEntryRepository rankingEntryRepository) {
        this.playerRepository = playerRepository;
        this.rankingEntryRepository = rankingEntryRepository;
    }

    @ModelAttribute
    public void currentMode(@AuthenticationPrincipal AuthUser authUser, HttpServletRequest request,
                            HttpServletResponse response, Model model) {
        LeagueMode cookieMode = ModeCookie.read(request);
        LeagueMode mode = pageMode(authUser, request).orElse(cookieMode);
        if (mode != cookieMode) {
            ModeCookie.write(request, response, mode);
        }
        model.addAttribute("currentMode", mode);
        model.addAttribute("modes", LeagueMode.values());
        model.addAttribute("currentModeAvailable", LeagueRulesets.isAvailable(mode));
        if (LeagueRulesets.isAvailable(mode)) {
            model.addAttribute("terms", LeagueRulesets.of(mode).terms());
            model.addAttribute("rules", LeagueRulesets.of(mode));         // 판정 주기·시즌 길이 등 화면 계산용
        }
    }

    /** 특정 선수의 화면이면 그 선수의 모드 */
    private Optional<LeagueMode> pageMode(AuthUser authUser, HttpServletRequest request) {
        Long playerId = playerIdOf(request);
        if (playerId == null) {
            return Optional.empty();
        }
        // servletPath 는 실행 환경(MockMvc 등)에 따라 비어 있을 수 있어 URI 에서 context-path(/game)를 뺀다
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/players/") && authUser != null) {
            return playerRepository.findByIdAndUserId(playerId, authUser.getId()).map(Player::getLeagueMode);
        }
        if (path.startsWith("/profiles/")) {
            return rankingEntryRepository.findByPlayerId(playerId).map(RankingEntry::getLeagueMode);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static Long playerIdOf(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(vars instanceof Map<?, ?> map) || !(map.get("playerId") instanceof String value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
