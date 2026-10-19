package com.mlbgame.postseason;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.award.Award;
import com.mlbgame.award.AwardService;
import com.mlbgame.common.Messages;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.SeasonProgressException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/players/{playerId}/postseason")
public class PostseasonController {

    private final PlayerService playerService;
    private final PostseasonService postseasonService;
    private final TeamRepository teamRepository;
    private final AwardService awardService;

    public PostseasonController(PlayerService playerService, PostseasonService postseasonService,
                                TeamRepository teamRepository, AwardService awardService) {
        this.playerService = playerService;
        this.postseasonService = postseasonService;
        this.teamRepository = teamRepository;
        this.awardService = awardService;
    }

    @GetMapping
    public String overview(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model,
                           RedirectAttributes redirectAttributes) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        Postseason postseason;
        try {
            postseason = postseasonService.getOrStart(authUser.getId(), playerId);
        } catch (SeasonProgressException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/players/" + playerId + "/season";
        }
        Map<Long, Team> teams = teamRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        List<Award> postseasonAwards = awardService.season(playerId, postseason.getSeasonYear()).stream()
                .filter(a -> a.getAfterSeries() > 54).toList();

        model.addAttribute("player", player);
        model.addAttribute("postseason", postseason);
        model.addAttribute("bracket", postseasonService.bracket(postseason));
        model.addAttribute("rounds", PostseasonRound.values());
        model.addAttribute("teams", teams);
        model.addAttribute("batting", postseasonService.batting(postseason));
        model.addAttribute("pitching", postseasonService.pitching(postseason));
        model.addAttribute("awards", postseasonAwards);
        model.addAttribute("nextRound", postseason.isDone() ? null : PostseasonRound.of(postseason.getNextRound()));
        return "postseason/overview";
    }

    @PostMapping("/play")
    public String play(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                       RedirectAttributes redirectAttributes) {
        try {
            postseasonService.playNextRound(authUser.getId(), playerId);
        } catch (SeasonProgressException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", Messages.get("error.inProgress"));
        }
        return "redirect:/players/" + playerId + "/postseason";
    }
}
