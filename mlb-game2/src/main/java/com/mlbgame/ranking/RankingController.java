package com.mlbgame.ranking;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.career.CareerService;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.mode.ModeCookie;
import com.mlbgame.ranking.RankingService.Period;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** 랭킹(은퇴 선수 스냅샷) + 다른 유저 선수의 읽기 전용 프로필 */
@Controller
public class RankingController {

    private final RankingService rankingService;
    private final CareerService careerService;
    private final TeamRepository teamRepository;

    public RankingController(RankingService rankingService, CareerService careerService, TeamRepository teamRepository) {
        this.rankingService = rankingService;
        this.careerService = careerService;
        this.teamRepository = teamRepository;
    }

    @GetMapping("/rankings")
    public String rankings(@AuthenticationPrincipal AuthUser authUser,
                           @RequestParam(defaultValue = "EARNINGS") RankingCategory category,
                           @RequestParam(defaultValue = "ALL") Period period, HttpServletRequest request,
                           Model model) {
        LeagueMode mode = ModeCookie.read(request);                   // 상단 탭의 모드 랭킹
        model.addAttribute("category", category);
        model.addAttribute("period", period);
        model.addAttribute("categories", RankingCategory.values());
        model.addAttribute("periods", Period.values());
        model.addAttribute("rows", rankingService.top(category, period, mode));
        model.addAttribute("myUserId", authUser == null ? null : authUser.getId());   // 비로그인 열람 가능
        return "ranking/list";
    }

    /** 랭킹에 오른(은퇴한) 선수만 공개. 커리어 기록 + 은퇴 시점 능력치 모두 */
    @GetMapping("/profiles/{playerId}")
    public String profile(@PathVariable Long playerId, Model model) {
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        model.addAttribute("summary", careerService.publicSummary(playerId));
        model.addAttribute("teams", teams);
        model.addAttribute("readOnly", true);
        return "career/overview";
    }
}
