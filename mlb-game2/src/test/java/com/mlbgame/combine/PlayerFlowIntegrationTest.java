package com.mlbgame.combine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.common.Dice;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldRepository;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.WorldTeamRepository;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import com.mlbgame.player.Position;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.PlayerStage;
import com.mlbgame.player.PlayerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선수 생성 → 컴바인 → 드래프트 전체 흐름.
 * 랜덤은 항상 0을 돌려주는 Dice 로 고정한다: 당일 컨디션 최악(-8), 종목 편차 0, 스카우트 선호 TEAM, 1번 순번, 첫 번째 팀.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlayerFlowIntegrationTest {

    @TestConfiguration
    static class FixedDiceConfig {
        @Bean
        @Primary
        Dice fixedDice() {
            return new Dice() {
                @Override
                public int nextInt(int bound) {
                    return 0;
                }

                @Override
                public double nextDouble() {
                    return 0;
                }

                @Override
                public double nextGaussian() {
                    return 0;
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    CombineSummaryRepository summaryRepository;

    @Autowired
    DraftResultRepository draftResultRepository;

    @Autowired
    LeagueWorldRepository leagueWorldRepository;

    @Autowired
    WorldTeamRepository worldTeamRepository;

    @Autowired
    NpcPlayerRepository npcPlayerRepository;

    @Autowired
    EarningsService earningsService;

    User owner;
    RequestPostProcessor asOwner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner", "{noop}pw", "구단주"));
        asOwner = user(new AuthUser(owner));
    }

    /** 포지션 능력치 11개에 각각 points 만큼 배분 */
    private static MockHttpServletRequestBuilder allocateEach(MockHttpServletRequestBuilder request,
                                                              Position position, int points) {
        for (Attribute attribute : Attribute.forPosition(position)) {
            request.param("allocation[" + attribute.name() + "]", String.valueOf(points));
        }
        return request;
    }

    private MockHttpServletRequestBuilder shortstopRequest() {
        return post("/players").with(csrf()).with(asOwner)
                .param("name", "김유격")
                .param("uniformNumber", "7")
                .param("position", "SS")
                .param("throwsHand", "R")
                .param("bats", "L")
                .param("nationality", "KOREA")
                .param("education", "COLLEGE")
                .param("schoolRegion", "US_WEST");
    }

    /** 대졸 기본 35 + 능력치마다 5씩 배분 → 모든 능력치 40 */
    private Long createCollegeShortstop() throws Exception {
        mockMvc.perform(allocateEach(shortstopRequest(), Position.SS, 5))
                .andExpect(status().is3xxRedirection());
        return playerRepository.findByUserIdAndStatusOrderByIdAsc(owner.getId(), PlayerStatus.ACTIVE)
                .get(0).getId();
    }

    @Test
    void 생성부터_드래프트까지_진행된다() throws Exception {
        Long playerId = createCollegeShortstop();
        Player created = playerRepository.findById(playerId).orElseThrow();
        assertThat(created.getOverall()).isEqualTo(40.0);

        // 컴바인: 종목 점수 40 - 8 = 32, 인터뷰 적중 +6 → 38.0 (C 등급)
        mockMvc.perform(post("/players/{id}/combine", playerId).with(csrf()).with(asOwner).param("answer", "TEAM"))
                .andExpect(redirectedUrl("/players/" + playerId + "/combine"));
        CombineSummary summary = summaryRepository.findById(playerId).orElseThrow();
        assertThat(summary.getTotalScore()).isEqualTo(38.0);
        assertThat(summary.getGrade()).isEqualTo(CombineGrade.C);
        assertThat(summary.isInterviewMatched()).isTrue();

        // 컴바인은 능력치를 바꾸지 않는다
        assertThat(playerRepository.findById(playerId).orElseThrow().getOverall()).isEqualTo(40.0);

        // 드래프트: C 등급 38점 → 12라운드 1번 (전체 331순위), 대졸이지만 11라운드 이하라 루키리그
        mockMvc.perform(post("/players/{id}/draft", playerId).with(csrf()).with(asOwner))
                .andExpect(redirectedUrl("/players/" + playerId + "/draft"));
        DraftResult draft = draftResultRepository.findById(playerId).orElseThrow();
        assertThat(draft.getRound()).isEqualTo(12);
        assertThat(draft.getOverallPick()).isEqualTo(331);
        assertThat(draft.getStartLevel()).isEqualTo(LeagueLevel.ROOKIE);

        Player drafted = playerRepository.findById(playerId).orElseThrow();
        assertThat(drafted.getStage()).isEqualTo(PlayerStage.DRAFTED);
        assertThat(drafted.getTeam()).isNotNull();
        assertThat(drafted.getTeamDisplayName()).isEqualTo("볼티모어 오리올스 루키");

        // 드래프트가 끝나면 이 커리어의 2026시즌 리그 월드가 생성된다 (30개 구단 × 26명)
        LeagueWorld world = leagueWorldRepository.findByPlayerId(playerId).orElseThrow();
        assertThat(world.getCurrentYear()).isEqualTo(2026);
        assertThat(worldTeamRepository.findByWorldIdOrderByIdAsc(world.getId())).hasSize(30);
        assertThat(npcPlayerRepository.countByWorldId(world.getId())).isEqualTo(30 * 26);
        // 계약금은 2025년 수령 원장에 기록된다
        assertThat(earningsService.careerTotal(playerId)).isEqualTo(draft.getSigningBonus());

        mockMvc.perform(get("/players/{id}/draft", playerId).with(asOwner)).andExpect(view().name("draft/result"));
        mockMvc.perform(get("/players/{id}", playerId).with(asOwner)).andExpect(view().name("players/detail"));
    }

    @Test
    void 컴바인은_한_번만_진행된다() throws Exception {
        Long playerId = createCollegeShortstop();
        mockMvc.perform(post("/players/{id}/combine", playerId).with(csrf()).with(asOwner).param("answer", "TEAM"));
        mockMvc.perform(post("/players/{id}/combine", playerId).with(csrf()).with(asOwner).param("answer", "LEGEND"))
                .andExpect(redirectedUrl("/players/" + playerId + "/combine"));

        assertThat(summaryRepository.findById(playerId).orElseThrow().getInterviewAnswer())
                .isEqualTo(InterviewAnswer.TEAM);
    }

    @Test
    void 다른_유저의_선수는_볼_수_없다() throws Exception {
        Long playerId = createCollegeShortstop();
        User stranger = userRepository.save(new User("stranger", "{noop}pw", "남의집"));

        mockMvc.perform(get("/players/{id}", playerId).with(user(new AuthUser(stranger))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/players/{id}/combine", playerId).with(csrf()).with(user(new AuthUser(stranger)))
                        .param("answer", "TEAM"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 배분_포인트를_다_쓰지_않으면_생성되지_않는다() throws Exception {
        mockMvc.perform(allocateEach(shortstopRequest(), Position.SS, 4)) // 44 포인트만 사용
                .andExpect(view().name("players/new"))
                .andExpect(model().attributeHasFieldErrors("form", "allocation"));

        assertThat(playerRepository.count()).isZero();
    }

    @Test
    void 능력치_하나에_한도를_넘겨_배분할_수_없다() throws Exception {
        mockMvc.perform(shortstopRequest()
                        .param("allocation[POWER]", "30")
                        .param("allocation[CONTACT]", "25"))
                .andExpect(view().name("players/new"))
                .andExpect(model().attributeHasFieldErrors("form", "allocation"));
    }

    @Test
    void 한_능력치에_몰아서_배분할_수_있다() throws Exception {
        mockMvc.perform(shortstopRequest()
                        .param("allocation[POWER]", "25")
                        .param("allocation[GAP_POWER]", "25")
                        .param("allocation[CONTACT]", "5"))
                .andExpect(status().is3xxRedirection());

        Player slugger = playerRepository.findByUserIdAndStatusOrderByIdAsc(owner.getId(), PlayerStatus.ACTIVE).get(0);
        assertThat(slugger.attribute(Attribute.POWER)).isEqualTo(60);
        assertThat(slugger.attribute(Attribute.SPEED)).isEqualTo(35);
        assertThat(slugger.getOverall()).isEqualTo(40.0);
    }

    @Test
    void 투수는_서로_다른_구종_3개를_골라야_한다() throws Exception {
        mockMvc.perform(post("/players").with(csrf()).with(asOwner)
                        .param("name", "최에이스").param("uniformNumber", "1").param("position", "SP")
                        .param("throwsHand", "R").param("bats", "R").param("nationality", "JAPAN")
                        .param("education", "HIGH_SCHOOL").param("schoolRegion", "OVERSEAS")
                        .param("pitch2", "SLIDER").param("pitch3", "SLIDER").param("pitch4", "CURVE"))
                .andExpect(view().name("players/new"))
                .andExpect(model().attributeHasFieldErrors("form", "pitchSelectionValid"));
    }

    @Test
    void 활성_선수는_3명까지만_만들_수_있다() throws Exception {
        for (int i = 0; i < 3; i++) {
            createCollegeShortstop();
        }
        mockMvc.perform(post("/players").with(csrf()).with(asOwner)
                        .param("name", "네번째").param("uniformNumber", "4").param("position", "OF")
                        .param("throwsHand", "R").param("bats", "R").param("nationality", "USA")
                        .param("education", "COLLEGE").param("schoolRegion", "US_EAST"))
                .andExpect(view().name("players/new"))
                .andExpect(model().attributeExists("errorMessage"));
    }
}
