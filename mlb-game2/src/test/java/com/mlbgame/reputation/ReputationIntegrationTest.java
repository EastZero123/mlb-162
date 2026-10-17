package com.mlbgame.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.common.Dice;
import com.mlbgame.contract.ContractOffer;
import com.mlbgame.contract.ContractOffer.Kind;
import com.mlbgame.contract.ContractOfferRepository;
import com.mlbgame.contract.ContractService;
import com.mlbgame.contract.ContractType;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.standings.StandingsService;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.WorldTeamRepository;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonService;
import com.mlbgame.trade.TradeEvent;
import com.mlbgame.trade.TradeEventRepository;
import com.mlbgame.trade.TradeService;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 평판 변화(계약·트레이드·시즌 결산)와 트레이드 거부 조항 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class ReputationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ContractService contractService;

    @Autowired
    ContractOfferRepository offerRepository;

    @Autowired
    ReputationService reputationService;

    @Autowired
    ReputationLogRepository logRepository;

    @Autowired
    SeasonService seasonService;

    @Autowired
    LeagueWorldService leagueWorldService;

    @Autowired
    StandingsService standingsService;

    @Autowired
    WorldTeamRepository worldTeamRepository;

    @Autowired
    NpcPlayerRepository npcPlayerRepository;

    @Autowired
    TradeEventRepository tradeEventRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    User owner;
    List<Team> teams;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("famous", "{noop}pw", "평판맨"));
        teams = teamRepository.findAllByOrderByIdAsc();
    }

    private Player mlbPlayer() {
        Player player = Player.create(owner.getId(), "이평판", 30, Position.OF, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_WEST, 75, Map.of(), List.of());
        player.completeCombine();
        player.draft(teams.get(3), LeagueLevel.MLB);
        return playerRepository.save(player);
    }

    private ContractOffer offer(Player player, Kind kind, Team team, int years, long salary, long fair) {
        return offerRepository.save(new ContractOffer(player.getId(), 2027, kind, team, years, salary, fair));
    }

    /** 결과가 항상 같은 주사위 — 0 이면 확률 판정이 모두 통과, 0.999 면 모두 실패 */
    private static Dice fixed(double value) {
        return new Dice() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public double nextGaussian() {
                return 0;
            }
        };
    }

    private TradeService tradeService(double dice) {
        return new TradeService(standingsService, worldTeamRepository, npcPlayerRepository, tradeEventRepository,
                fixed(dice), reputationService);
    }

    // ------------------------------------------------------------ 계약

    @Test
    void 제시_조건_그대로_수락하면_평판_플러스2와_이력이_남는다() {
        Player player = mlbPlayer();
        ContractOffer ext = offer(player, Kind.EXTENSION, player.getTeam(), 4, 12_000_000, 12_000_000);

        contractService.accept(owner.getId(), player.getId(), ext.getId());

        assertThat(player.getReputation()).isEqualTo(52);
        assertThat(reputationService.recent(player.getId())).singleElement()
                .satisfies(log -> {
                    assertThat(log.getDelta()).isEqualTo(2);
                    assertThat(log.getReputationAfter()).isEqualTo(52);
                });
    }

    @Test
    void 청문회는_이기면_평판_마이너스3_지면_그대로() {
        Player player = mlbPlayer();
        ContractOffer win = offer(player, Kind.ARBITRATION, player.getTeam(), 1, 3_000_000, 4_000_000);
        contractService.arbitrate(owner.getId(), player.getId(), win.getId(), 4_200_000);   // 적정에 더 가까움 → 승리
        assertThat(player.getReputation()).isEqualTo(47);

        ContractOffer lose = offer(player, Kind.ARBITRATION, player.getTeam(), 1, 3_000_000, 4_000_000);
        contractService.arbitrate(owner.getId(), player.getId(), lose.getId(), 9_000_000);  // 너무 높음 → 패배
        assertThat(player.getReputation()).isEqualTo(47);
        assertThat(logRepository.findByPlayerIdOrderByIdAsc(player.getId())).hasSize(1);
    }

    @Test
    void 역제안이_결렬되면_평판_마이너스5() {
        Player player = mlbPlayer();
        ContractOffer fa = offer(player, Kind.FREE_AGENCY, teams.get(7), 3, 20_000_000, 20_000_000);

        ContractOffer result = contractService.counter(owner.getId(), player.getId(), fa.getId(), 30_000_000);  // 50% 초과 → 결렬

        assertThat(result.getStatus()).isEqualTo(ContractOffer.Status.REJECTED);
        assertThat(player.getReputation()).isEqualTo(45);
        assertThat(result.getNote()).contains("평판 -5");
    }

    @Test
    void 연장_제안_거절은_마이너스2_FA_오퍼_거절은_그대로() {
        Player player = mlbPlayer();
        ContractOffer fa = offer(player, Kind.FREE_AGENCY, teams.get(7), 3, 20_000_000, 20_000_000);
        ContractOffer fa2 = offer(player, Kind.FREE_AGENCY, teams.get(8), 3, 20_000_000, 20_000_000);
        contractService.decline(owner.getId(), player.getId(), fa.getId());
        assertThat(player.getReputation()).isEqualTo(50);

        ContractOffer ext = offer(player, Kind.EXTENSION, player.getTeam(), 4, 12_000_000, 12_000_000);
        contractService.decline(owner.getId(), player.getId(), ext.getId());
        assertThat(player.getReputation()).isEqualTo(48);
        assertThat(fa2.isPending()).isTrue();
    }

    @Test
    void 평판은_0_아래로_내려가지_않고_이력에는_실제_변화량이_남는다() {
        Player player = mlbPlayer();
        player.changeReputation(-47);                                    // 3
        ContractOffer fa = offer(player, Kind.FREE_AGENCY, teams.get(7), 3, 20_000_000, 20_000_000);

        contractService.counter(owner.getId(), player.getId(), fa.getId(), 30_000_000);

        assertThat(player.getReputation()).isZero();
        assertThat(reputationService.recent(player.getId()).get(0).getDelta()).isEqualTo(-3);
    }

    // ------------------------------------------------------------ 트레이드 거부 조항

    @Test
    void 거부_조항을_넣고_계약하면_선수와_계약_이력에_남고_다음_계약에서_다시_정해진다() {
        Player player = mlbPlayer();
        ContractOffer ext = offer(player, Kind.EXTENSION, player.getTeam(), 4, 12_000_000, 12_000_000);

        contractService.accept(owner.getId(), player.getId(), ext.getId(), true);

        assertThat(player.isNoTradeClause()).isTrue();
        assertThat(ext.isNoTradeClause()).isTrue();
        assertThat(ext.getNote()).contains("트레이드 거부 조항");
        assertThat(player.getSalary()).isEqualTo(12_000_000);           // 연봉 영향 없음

        ContractOffer next = offer(player, Kind.ARBITRATION, player.getTeam(), 1, 3_000_000, 4_000_000);
        contractService.accept(owner.getId(), player.getId(), next.getId(), false);
        assertThat(player.isNoTradeClause()).isFalse();
    }

    @Test
    void 거부_조항이_있으면_구단_주도_트레이드와_루머가_없다() {
        Player player = mlbPlayer();
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());
        var world = leagueWorldService.getOrCreate(player.getId());
        TradeService alwaysHits = tradeService(0.0);                    // 확률 판정이 항상 통과

        assertThat(alwaysHits.afterSeries(player, season, 10, world, Map.of())).isNotNull();   // 조항 없으면 루머

        player.signContract(null, 10_000_000, 3, ContractType.EXTENSION, true);
        assertThat(alwaysHits.afterSeries(player, season, 11, world, Map.of())).isNull();
        assertThat(alwaysHits.afterSeries(player, season, 12, world, Map.of())).isNull();
        assertThat(player.getTeam().getId()).isEqualTo(teams.get(3).getId());
    }

    @Test
    void 거부_조항이_있어도_직접_요청은_되고_성사되면_조항이_소멸하며_평판_마이너스2() {
        Player player = mlbPlayer();
        player.signContract(null, 10_000_000, 3, ContractType.EXTENSION, true);
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());

        TradeEvent event = tradeService(0.0).request(player, season, leagueWorldService.getOrCreate(player.getId()), Map.of());

        assertThat(event.getKind()).isEqualTo(TradeEvent.Kind.TRADE);
        assertThat(event.getNote()).contains("거부 조항 소멸");
        assertThat(player.isNoTradeClause()).isFalse();
        assertThat(player.getReputation()).isEqualTo(48);
    }

    @Test
    void 트레이드_요청이_거절되면_평판_마이너스8() {
        Player player = mlbPlayer();
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());

        TradeEvent event = tradeService(0.999).request(player, season, leagueWorldService.getOrCreate(player.getId()),
                Map.of());

        assertThat(event.getKind()).isEqualTo(TradeEvent.Kind.REQUEST_DENIED);
        assertThat(player.getReputation()).isEqualTo(42);
        assertThat(event.getNote()).contains("평판 -8");
    }

    // ------------------------------------------------------------ 시즌 결산

    @Test
    void 시즌_결산_우승_플러스5_메이저_풀타임_플러스1() {
        Player player = mlbPlayer();
        PlayerSeason season = PlayerSeason.start(player.getId(), 2026, LeagueLevel.MLB, player.getTeam().getId());
        ReflectionTestUtils.setField(season, "mlbSeriesPlayed", 40);

        List<String> news = reputationService.seasonReview(player, season, true);

        assertThat(player.getReputation()).isEqualTo(56);
        assertThat(news).singleElement().asString().contains("우승 +5", "메이저 풀타임 +1", "56");
    }

    @Test
    void 아무_일도_없던_시즌은_결산_소식이_없다() {
        Player player = mlbPlayer();
        PlayerSeason season = PlayerSeason.start(player.getId(), 2026, LeagueLevel.AAA, player.getTeam().getId());

        assertThat(reputationService.seasonReview(player, season, false)).isEmpty();
        assertThat(player.getReputation()).isEqualTo(50);
    }

    // ------------------------------------------------------------ 화면

    @Test
    void 계약_화면에_평판과_거부_조항_선택이_나오고_체크하면_조항이_들어간다() throws Exception {
        Player player = mlbPlayer();
        ContractOffer ext = offer(player, Kind.EXTENSION, player.getTeam(), 4, 12_000_000, 12_000_000);

        mockMvc.perform(get("/players/{id}/contract", player.getId()).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<h2 class=\"section-title\">평판</h2>")))
                .andExpect(content().string(Matchers.containsString("트레이드 거부 조항 포함")))
                .andExpect(content().string(Matchers.containsString("data-ntc=\"ntc-" + ext.getId() + "\"")));

        mockMvc.perform(post("/players/{id}/contract/{o}/accept", player.getId(), ext.getId())
                        .with(csrf()).with(user(new AuthUser(owner))).param("noTradeClause", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(player.isNoTradeClause()).isTrue();
    }

    @Test
    void 시즌_화면에_트레이드_요청_성공_확률이_나온다() throws Exception {
        Player player = mlbPlayer();
        mockMvc.perform(get("/players/{id}/season", player.getId()).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("트레이드 요청 (성공 약 30%)")));
    }
}
