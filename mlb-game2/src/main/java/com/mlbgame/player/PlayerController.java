package com.mlbgame.player;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.common.Messages;
import com.mlbgame.config.GameBalance;
import jakarta.validation.Valid;
import java.util.Arrays;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/players")
public class PlayerController {

    private final PlayerService playerService;
    private final GameBalance balance;

    public PlayerController(PlayerService playerService, GameBalance balance) {
        this.playerService = playerService;
        this.balance = balance;
    }

    @ModelAttribute
    public void formOptions(Model model) {
        model.addAttribute("hitterAttributes", Attribute.forPosition(Position.SS));
        model.addAttribute("pitcherAttributes", Attribute.forPosition(Position.SP));
        model.addAttribute("allocationPoints", balance.allocationPoints());
        model.addAttribute("allocationMax", balance.allocationMaxPerAttribute());
        model.addAttribute("highSchoolStart", balance.highSchoolStart());
        model.addAttribute("collegeStart", balance.collegeStart());
        model.addAttribute("positions", Arrays.stream(Position.values()).filter(Position::isUserSelectable).toList());
        model.addAttribute("batHands", Hand.values());
        model.addAttribute("throwHands", new Hand[]{Hand.R, Hand.L});
        model.addAttribute("nationalities", Nationality.values());
        model.addAttribute("educations", Education.values());
        model.addAttribute("schoolRegions", SchoolRegion.values());
        model.addAttribute("pitchTypes", Arrays.stream(PitchType.values()).filter(PitchType::isSelectable).toList());
    }

    @GetMapping("/new")
    public String createPage(@AuthenticationPrincipal AuthUser authUser, @ModelAttribute("form") PlayerCreateForm form,
                             RedirectAttributes redirectAttributes) {
        if (!playerService.hasFreeSlot(authUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", Messages.get("create.error.slotsFull"));
            return "redirect:/dashboard";
        }
        return "players/new";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthUser authUser,
                         @Valid @ModelAttribute("form") PlayerCreateForm form, BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            return "players/new";
        }
        try {
            Player player = playerService.create(authUser.getId(), form);
            return "redirect:/players/" + player.getId() + "/combine";
        } catch (InvalidAllocationException e) {
            bindingResult.rejectValue("allocation", "invalid", e.getMessage());
            return "players/new";
        } catch (SlotLimitExceededException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "players/new";
        }
    }

    @GetMapping("/{playerId}")
    public String detail(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        model.addAttribute("player", playerService.getOwned(authUser.getId(), playerId));
        return "players/detail";
    }
}
