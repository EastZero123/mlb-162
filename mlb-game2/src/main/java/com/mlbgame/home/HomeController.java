package com.mlbgame.home;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.config.GameBalance;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final PlayerService playerService;
    private final GameBalance balance;

    public HomeController(PlayerService playerService, GameBalance balance) {
        this.playerService = playerService;
        this.balance = balance;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AuthUser authUser, Model model) {
        List<Player> players = playerService.listActive(authUser.getId());
        model.addAttribute("nickname", authUser.getNickname());
        model.addAttribute("players", players);
        model.addAttribute("emptySlots", Math.max(0, balance.maxActivePlayers() - players.size()));
        model.addAttribute("retiredPlayers", playerService.listRetired(authUser.getId()));
        return "dashboard";
    }
}
