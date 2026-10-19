package com.mlbgame.career;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.common.Messages;
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
        if (!"은퇴".equals(confirm.trim()) && !"retire".equalsIgnoreCase(confirm.trim())) {   // 영어 화면은 retire
            ra.addFlashAttribute("errorMessage", Messages.get("career.retire.confirmError"));
            return "redirect:/players/" + playerId + "/career";
        }
        try {
            Player player = careerService.retire(authUser.getId(), playerId);
            ra.addFlashAttribute("headlines", List.of(Messages.get("career.retire.done", player.getName())));
        } catch (SeasonProgressException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/players/" + playerId + "/career";
        }
        return "redirect:/dashboard";
    }
}
