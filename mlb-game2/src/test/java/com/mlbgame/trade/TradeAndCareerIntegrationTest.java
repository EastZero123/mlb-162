package com.mlbgame.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.career.CareerService;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
import com.mlbgame.training.TrainingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TradeAndCareerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TradeService tradeService;

    @Autowired
    CareerService careerService;

    @Autowired
    SeasonService seasonService;

    @Autowired
    TrainingService trainingService;

    @Autowired
    PlayerService playerService;

    @Autowired
    LeagueWorldService leagueWorldService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("tradee", "{noop}pw", "이적생"));
    }

    private Player mlbPlayer() {
        Player player = Player.create(owner.getId(), "윤이적", 17, Position.B3, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(12), LeagueLevel.MLB);
        return playerRepository.save(player);
    }

    @Test
    void 트레이드_요청은_성사되거나_거절되고_시즌당_한_번뿐이다() {
        Player player = mlbPlayer();
        Long originalTeam = player.getTeam().getId();
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());

        TradeEvent event = tradeService.request(player, season, leagueWorldService.getOrCreate(player.getId()), Map.of());
        if (event.getKind() == TradeEvent.Kind.TRADE) {
            assertThat(player.getTeam().getId()).isNotEqualTo(originalTeam);
            assertThat(season.getTeamId()).isEqualTo(player.getTeam().getId());
            assertThat(player.getLeagueLevel()).isEqualTo(LeagueLevel.MLB);
        } else {
            assertThat(event.getKind()).isEqualTo(TradeEvent.Kind.REQUEST_DENIED);
            assertThat(player.getTeam().getId()).isEqualTo(originalTeam);
        }
        assertThat(tradeService.canRequest(player, season)).isFalse();
        assertThatThrownBy(() -> tradeService.request(player, season, leagueWorldService.getOrCreate(player.getId()), Map.of()))
                .isInstanceOf(SeasonProgressException.class);
    }

    @Test
    void 트레이드_기간이_아니면_이벤트가_없다() {
        Player player = mlbPlayer();
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());
        assertThat(tradeService.afterSeries(player, season, 3, leagueWorldService.getOrCreate(player.getId()), Map.of()))
                .isNull();
    }

    @Test
    void 트레이드되면_다음_시리즈부터_새_구단으로_뛴다() {
        Player player = mlbPlayer();
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());
        TradeEvent event = tradeService.request(player, season, leagueWorldService.getOrCreate(player.getId()), Map.of());
        if (event.getKind() != TradeEvent.Kind.TRADE) {
            return;                                                  // 거절된 경우는 위 테스트에서 확인
        }
        seasonService.playNextSeries(owner.getId(), player.getId());
        assertThat(seasonService.matchups(owner.getId(), player.getId())).allSatisfy(
                m -> assertThat(m.opponent().getId()).isNotEqualTo(player.getTeam().getId()));
    }

    @Test
    void 은퇴하면_슬롯이_비고_시리즈를_진행할_수_없으며_커리어는_남는다() throws Exception {
        Player player = mlbPlayer();
        for (int i = 0; i < 2; i++) {
            seasonService.playNextSeries(owner.getId(), player.getId());
            trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT);
        }

        mockMvc.perform(post("/players/{id}/retire", player.getId()).with(csrf()).with(user(new AuthUser(owner)))
                        .param("confirm", "아니요"))
                .andExpect(redirectedUrl("/players/" + player.getId() + "/career"));
        assertThat(player.isRetired()).isFalse();

        mockMvc.perform(post("/players/{id}/retire", player.getId()).with(csrf()).with(user(new AuthUser(owner)))
                        .param("confirm", "은퇴"))
                .andExpect(redirectedUrl("/dashboard"));

        Player retired = playerRepository.findById(player.getId()).orElseThrow();
        assertThat(retired.isRetired()).isTrue();
        assertThat(playerService.listActive(owner.getId())).isEmpty();
        assertThat(playerService.listRetired(owner.getId())).hasSize(1);
        assertThatThrownBy(() -> seasonService.playNextSeries(owner.getId(), player.getId()))
                .isInstanceOf(SeasonProgressException.class);

        CareerService.Summary summary = careerService.summary(owner.getId(), player.getId());
        assertThat(summary.seasons()).hasSize(1);
        assertThat(summary.mlbBatting().games).isPositive();

        mockMvc.perform(get("/players/{id}/career", player.getId()).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(view().name("career/overview"));
        mockMvc.perform(get("/dashboard").with(user(new AuthUser(owner))))
                .andExpect(status().isOk());
    }
}
