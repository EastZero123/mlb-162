package com.mlbgame.contract;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.training.InvalidTrainingException;
import java.util.function.Supplier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 계약 화면: 현재 계약, 서비스 연수, 받은 제안(수락·청문회·역제안·거절), 계약 이력 */
@Controller
@RequestMapping("/players/{playerId}/contract")
public class ContractController {

    private final PlayerService playerService;
    private final ContractService contractService;
    private final EarningsService earningsService;

    public ContractController(PlayerService playerService, ContractService contractService,
                              EarningsService earningsService) {
        this.playerService = playerService;
        this.contractService = contractService;
        this.earningsService = earningsService;
    }

    @GetMapping
    public String overview(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        model.addAttribute("player", player);
        model.addAttribute("serviceYears", contractService.serviceYears(playerId));
        model.addAttribute("offers", contractService.pending(playerId));
        model.addAttribute("history", contractService.history(playerId));
        model.addAttribute("careerEarnings", earningsService.careerTotal(playerId));
        return "contract/overview";
    }

    @PostMapping("/{offerId}/accept")
    public String accept(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                         @PathVariable Long offerId, RedirectAttributes ra) {
        return handle(playerId, ra, () -> contractService.accept(authUser.getId(), playerId, offerId));
    }

    @PostMapping("/{offerId}/arbitrate")
    public String arbitrate(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                            @PathVariable Long offerId, @RequestParam long ask, RedirectAttributes ra) {
        return handle(playerId, ra, () -> contractService.arbitrate(authUser.getId(), playerId, offerId, ask));
    }

    @PostMapping("/{offerId}/counter")
    public String counter(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                          @PathVariable Long offerId, @RequestParam long ask, RedirectAttributes ra) {
        return handle(playerId, ra, () -> contractService.counter(authUser.getId(), playerId, offerId, ask));
    }

    @PostMapping("/{offerId}/decline")
    public String decline(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                          @PathVariable Long offerId, RedirectAttributes ra) {
        return handle(playerId, ra, () -> contractService.decline(authUser.getId(), playerId, offerId));
    }

    private String handle(Long playerId, RedirectAttributes ra, Supplier<ContractOffer> action) {
        try {
            ContractOffer offer = action.get();
            String message = offer.getStatus() == ContractOffer.Status.ACCEPTED
                    ? "계약 체결: " + offer.getTeam().getNameKo() + " " + offer.getYears() + "년 "
                    + ContractService.money(offer.getSalary()) + "/년" + (offer.getNote() != null ? " (" + offer.getNote() + ")" : "")
                    : offer.getNote();
            ra.addFlashAttribute("resultMessage", message);
        } catch (SeasonProgressException | InvalidTrainingException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            ra.addFlashAttribute("errorMessage", "이미 처리된 요청입니다.");
        }
        return "redirect:/players/" + playerId + "/contract";
    }
}
