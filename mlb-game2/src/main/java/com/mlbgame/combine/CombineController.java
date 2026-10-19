package com.mlbgame.combine;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.combine.CombineService.CombineReport;
import com.mlbgame.common.Messages;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.PlayerStage;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/players/{playerId}")
public class CombineController {

    private final PlayerService playerService;
    private final CombineService combineService;
    private final DraftService draftService;

    public CombineController(PlayerService playerService, CombineService combineService, DraftService draftService) {
        this.playerService = playerService;
        this.combineService = combineService;
        this.draftService = draftService;
    }

    /** 컴바인 전이면 안내·인터뷰 화면, 끝났으면 결과 화면 */
    @GetMapping("/combine")
    public String combine(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        model.addAttribute("player", player);
        if (player.getStage() == PlayerStage.CREATED) {
            model.addAttribute("tests", CombineTest.forPosition(player.getPosition()));
            model.addAttribute("question", InterviewAnswer.question());
            model.addAttribute("answers", InterviewAnswer.values());
            return "combine/intro";
        }
        CombineReport report = combineService.report(authUser.getId(), playerId);
        model.addAttribute("summary", report.summary());
        model.addAttribute("results", report.results());
        return "combine/result";
    }

    @PostMapping("/combine")
    public String runCombine(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                             @RequestParam(required = false) InterviewAnswer answer,
                             RedirectAttributes redirectAttributes) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        if (player.getStage() != PlayerStage.CREATED) {
            return "redirect:/players/" + playerId + "/combine";
        }
        if (answer == null) {
            redirectAttributes.addFlashAttribute("errorMessage", Messages.get("combine.error.noAnswer"));
            return "redirect:/players/" + playerId + "/combine";
        }
        combineService.run(authUser.getId(), playerId, answer);
        redirectAttributes.addFlashAttribute("justFinished", true);
        return "redirect:/players/" + playerId + "/combine";
    }

    @PostMapping("/draft")
    public String runDraft(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        if (player.getStage() == PlayerStage.COMBINE_DONE) {
            draftService.run(authUser.getId(), playerId);
        }
        return "redirect:/players/" + playerId + "/draft";
    }

    @GetMapping("/draft")
    public String draft(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        DraftResult result = draftService.find(authUser.getId(), playerId);
        if (result == null) {
            return "redirect:/players/" + playerId + "/combine";
        }
        model.addAttribute("player", player);
        model.addAttribute("draft", result);
        return "draft/result";
    }
}
