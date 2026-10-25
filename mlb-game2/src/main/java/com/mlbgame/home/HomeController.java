package com.mlbgame.home;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.common.Messages;
import com.mlbgame.config.GameBalance;
import com.mlbgame.contract.ContractService;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.mode.ModeCookie;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.PlayerSeasonRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final PlayerService playerService;
    private final PlayerSeasonRepository seasonRepository;
    private final ContractService contractService;
    private final GameBalance balance;

    public HomeController(PlayerService playerService, PlayerSeasonRepository seasonRepository,
                          ContractService contractService, GameBalance balance) {
        this.playerService = playerService;
        this.seasonRepository = seasonRepository;
        this.contractService = contractService;
        this.balance = balance;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    /** 패치노트 (로그인 없이 공개). 내용은 templates/patch-notes.html (영어: patch-notes-en.html) 에 직접 작성 */
    @GetMapping("/patch-notes")
    public String patchNotes() {
        return Messages.english() ? "patch-notes-en" : "patch-notes";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AuthUser authUser, HttpServletRequest request, Model model) {
        LeagueMode mode = ModeCookie.read(request);                   // 상단 탭 — 슬롯·목록은 모드별
        List<Player> players = playerService.listActive(authUser.getId(), mode);
        // 카드 요약: 가장 최근 시즌 진행 상황, 결정할 계약 제안
        Map<Long, PlayerSeason> seasons = new HashMap<>();
        Set<Long> pendingContracts = new HashSet<>();
        for (Player player : players) {
            List<PlayerSeason> history = seasonRepository.findByPlayerIdOrderBySeasonYearAsc(player.getId());
            if (!history.isEmpty()) {
                seasons.put(player.getId(), history.get(history.size() - 1));
            }
            if (contractService.hasPending(player.getId())) {
                pendingContracts.add(player.getId());
            }
        }
        model.addAttribute("nickname", authUser.getNickname());
        model.addAttribute("players", players);
        model.addAttribute("seasons", seasons);
        model.addAttribute("pendingContracts", pendingContracts);
        model.addAttribute("emptySlots", Math.max(0, balance.maxActivePlayers() - players.size()));
        model.addAttribute("retiredPlayers", playerService.listRetired(authUser.getId(), mode));
        return "dashboard";
    }
}
