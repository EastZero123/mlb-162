package com.mlbgame.career;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.player.Player;
import com.mlbgame.season.SeasonProgressException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CareerController {

    private final CareerService careerService;
    private final TeamRepository teamRepository;

    public CareerController(CareerService careerService, TeamRepository teamRepository) {
        this.careerService = careerService;
        this.teamRepository = teamRepository;
    }

    @GetMapping("/players/{playerId}/career")
    public String career(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        model.addAttribute("summary", careerService.summary(authUser.getId(), playerId));
        model.addAttribute("teams", teams);
        return "career/overview";
    }

    /** 자발적 은퇴 (확인 문구를 입력해야 진행) */
    @PostMapping("/players/{playerId}/retire")
    public String retire(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                         @RequestParam(defaultValue = "") String confirm, RedirectAttributes ra) {
        if (!"은퇴".equals(confirm.trim())) {
            ra.addFlashAttribute("errorMessage", "은퇴하려면 확인란에 '은퇴'를 입력해 주세요.");
            return "redirect:/players/" + playerId + "/career";
        }
        try {
            Player player = careerService.retire(authUser.getId(), playerId);
            ra.addFlashAttribute("headlines", List.of(player.getName() + " 선수가 은퇴했습니다. 기록은 커리어 화면에 영구 보관됩니다."));
        } catch (SeasonProgressException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/players/" + playerId + "/career";
        }
        return "redirect:/dashboard";
    }
}
