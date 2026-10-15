package com.mlbgame.season;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.award.AwardService;
import com.mlbgame.award.StatPointLog;
import com.mlbgame.award.StatPointService;
import com.mlbgame.contract.ContractService;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.trade.TradeEvent;
import com.mlbgame.trade.TradeService;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.SeasonService.SeriesDetail;
import com.mlbgame.season.SeasonService.SeriesOutcome;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.season.schedule.SeasonScheduleService.Matchup;
import com.mlbgame.season.transition.SeasonTransitionService;
import com.mlbgame.training.InvalidTrainingException;
import com.mlbgame.training.TrainingLog;
import com.mlbgame.training.TrainingService;
import java.util.List;
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

/**
 * 시즌 플레이 화면: 시즌 허브(다음 상대·진행 버튼·훈련) → 시리즈 결과(+훈련) → 시즌 기록.
 */
@Controller
@RequestMapping("/players/{playerId}/season")
public class SeasonController {

    private final PlayerService playerService;
    private final SeasonService seasonService;
    private final TrainingService trainingService;
    private final EarningsService earningsService;
    private final StatPointService statPointService;
    private final AwardService awardService;
    private final SeasonTransitionService transitionService;
    private final ContractService contractService;
    private final TradeService tradeService;
    private final LeagueWorldService leagueWorldService;

    public SeasonController(PlayerService playerService, SeasonService seasonService,
                            TrainingService trainingService, EarningsService earningsService,
                            StatPointService statPointService, AwardService awardService,
                            SeasonTransitionService transitionService, ContractService contractService,
                            TradeService tradeService, LeagueWorldService leagueWorldService) {
        this.transitionService = transitionService;
        this.contractService = contractService;
        this.tradeService = tradeService;
        this.leagueWorldService = leagueWorldService;
        this.playerService = playerService;
        this.seasonService = seasonService;
        this.trainingService = trainingService;
        this.earningsService = earningsService;
        this.statPointService = statPointService;
        this.awardService = awardService;
    }

    @GetMapping
    public String hub(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        PlayerSeason season = seasonService.currentSeason(authUser.getId(), playerId);
        List<Matchup> matchups = seasonService.matchups(authUser.getId(), playerId);
        model.addAttribute("player", player);
        model.addAttribute("season", season);
        model.addAttribute("next", season.isCompleted() ? null : matchups.get(season.getNextSeriesNo() - 1));
        model.addAttribute("upcoming", season.isCompleted() ? List.of()
                : matchups.subList(season.getNextSeriesNo() - 1, Math.min(matchups.size(), season.getNextSeriesNo() + 4)));
        model.addAttribute("readyForNext", transitionService.ready(playerId, season));
        model.addAttribute("contractPending", contractService.hasPending(playerId));
        model.addAttribute("canRequestTrade", !player.isRetired() && tradeService.canRequest(player, season));
        model.addAttribute("tradeEvents", tradeService.seasonEvents(playerId, season.getSeasonYear()));
        return "season/hub";
    }

    /** 트레이드 요청 (시즌당 1회, 마감 전까지) */
    @PostMapping("/trade-request")
    public String requestTrade(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                               RedirectAttributes redirectAttributes) {
        try {
            Player player = playerService.getOwned(authUser.getId(), playerId);
            PlayerSeason season = seasonService.currentSeason(authUser.getId(), playerId);
            TradeEvent event = tradeService.request(player, season, leagueWorldService.getOrCreate(playerId),
                    seasonService.mlbSeason(authUser.getId(), playerId).teamResults());
            redirectAttributes.addFlashAttribute(event.getKind() == TradeEvent.Kind.TRADE ? "trainedMessage" : "errorMessage",
                    event.getNote());
        } catch (SeasonProgressException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/players/" + playerId + "/season";
    }

    /** 다음 시즌으로 (정규시즌·포스트시즌을 마친 뒤) */
    @PostMapping("/next")
    public String nextSeason(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                             RedirectAttributes redirectAttributes) {
        try {
            SeasonTransitionService.Result result = transitionService.advance(authUser.getId(), playerId);
            redirectAttributes.addFlashAttribute("headlines", result.headlines());
            if (result.retired()) {
                return "redirect:/dashboard";
            }
        } catch (SeasonProgressException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 처리된 요청입니다.");
        }
        return "redirect:/players/" + playerId + "/season";
    }

    @PostMapping("/play")
    public String play(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                       RedirectAttributes redirectAttributes) {
        try {
            SeriesOutcome outcome = seasonService.playNextSeries(authUser.getId(), playerId);
            return "redirect:/players/" + playerId + "/season/series/" + outcome.record().getSeriesNo();
        } catch (SeasonProgressException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 진행 중인 요청이 있습니다. 잠시 후 다시 시도해 주세요.");
        }
        return "redirect:/players/" + playerId + "/season";
    }

    @PostMapping("/train")
    public String train(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                        @RequestParam Attribute attribute, RedirectAttributes redirectAttributes) {
        try {
            TrainingLog log = trainingService.train(authUser.getId(), playerId, attribute);
            Player player = playerService.getOwned(authUser.getId(), playerId);
            int progress = player.trainingProgress(attribute);
            redirectAttributes.addFlashAttribute("trainedMessage", progress == 0
                    ? attribute.getLabel() + " +1 → " + log.getValueAfter()
                    : attribute.getLabel() + " 훈련 " + progress + "/" + player.trainingCost(attribute)
                      + " (" + (player.trainingCost(attribute) - progress) + "회 더 하면 +1)");
        } catch (SeasonProgressException | InvalidTrainingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 처리된 요청입니다.");
        }
        return "redirect:/players/" + playerId + "/season";
    }

    /** 시상으로 받은 스탯업 포인트 사용 (언제든) */
    @PostMapping("/points")
    public String spendPoint(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                             @RequestParam Attribute attribute, RedirectAttributes redirectAttributes) {
        try {
            StatPointLog log = statPointService.spend(authUser.getId(), playerId, attribute);
            redirectAttributes.addFlashAttribute("trainedMessage",
                    "포인트 사용 — " + attribute.getLabel() + " +1 → " + log.getValueAfter());
        } catch (InvalidTrainingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 처리된 요청입니다.");
        }
        return "redirect:/players/" + playerId + "/season";
    }

    @GetMapping("/series/{seriesNo}")
    public String series(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId,
                         @PathVariable int seriesNo, Model model) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        SeriesDetail detail = seasonService.seriesDetail(authUser.getId(), playerId, seriesNo);
        model.addAttribute("player", player);
        model.addAttribute("season", detail.season());
        model.addAttribute("detail", detail);
        // 가장 최근 시리즈 결과 화면에서만 바로 훈련할 수 있게 한다
        model.addAttribute("canTrainHere",
                detail.season().isTrainingPending() && detail.season().getSeriesPlayed() == seriesNo);
        return "season/series";
    }

    @GetMapping("/stats")
    public String stats(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long playerId, Model model) {
        Player player = playerService.getOwned(authUser.getId(), playerId);
        PlayerSeason season = seasonService.currentSeason(authUser.getId(), playerId);
        model.addAttribute("player", player);
        model.addAttribute("season", season);
        model.addAttribute("batting", seasonService.batting(season));
        model.addAttribute("pitching", seasonService.pitching(season));
        model.addAttribute("fielding", seasonService.fielding(season));
        model.addAttribute("log", seasonService.seriesLog(authUser.getId(), playerId));
        model.addAttribute("trainings", trainingService.history(playerId, season.getSeasonYear()));
        model.addAttribute("careerEarnings", earningsService.careerTotal(playerId));
        model.addAttribute("levelHistory", seasonService.levelHistory(playerId));
        model.addAttribute("awards", awardService.career(playerId));
        return "season/stats";
    }
}
