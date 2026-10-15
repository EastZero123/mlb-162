package com.mlbgame.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.PitchType;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** 시즌 화면 흐름: 허브 → 시리즈 진행 → 결과(+훈련) → 허브 → 기록. 템플릿 렌더링 오류도 함께 잡는다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeasonControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    User owner;
    RequestPostProcessor asOwner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("screenowner", "{noop}pw", "화면주인"));
        asOwner = user(new AuthUser(owner));
    }

    private Player drafted(Position position, List<PitchType> pitches) {
        Player player = Player.create(owner.getId(), "박화면", 21, position, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_EAST, 40, Map.of(), pitches);
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(2), LeagueLevel.A);
        return playerRepository.save(player);
    }

    @Test
    void 타자로_시리즈_진행부터_훈련까지_화면이_이어진다() throws Exception {
        Long id = drafted(Position.SS, List.of()).getId();

        mockMvc.perform(get("/players/{id}/season", id).with(asOwner))
                .andExpect(status().isOk())
                .andExpect(view().name("season/hub"))
                .andExpect(content().string(Matchers.containsString("시리즈 진행")));

        mockMvc.perform(post("/players/{id}/season/play", id).with(csrf()).with(asOwner))
                .andExpect(redirectedUrl("/players/" + id + "/season/series/1"));

        mockMvc.perform(get("/players/{id}/season/series/1", id).with(asOwner))
                .andExpect(status().isOk())
                .andExpect(view().name("season/series"))
                .andExpect(model().attribute("canTrainHere", true))
                .andExpect(content().string(Matchers.containsString("1차전")))
                .andExpect(content().string(Matchers.containsString("훈련")));

        // 훈련 전에는 다음 시리즈 불가
        mockMvc.perform(post("/players/{id}/season/play", id).with(csrf()).with(asOwner))
                .andExpect(redirectedUrl("/players/" + id + "/season"))
                .andExpect(flash().attributeExists("errorMessage"));

        mockMvc.perform(post("/players/{id}/season/train", id).with(csrf()).with(asOwner).param("attribute", "CONTACT"))
                .andExpect(redirectedUrl("/players/" + id + "/season"))
                .andExpect(flash().attribute("trainedMessage", "컨택 +1 → 41"));
        assertThat(playerRepository.findById(id).orElseThrow().attribute(Attribute.CONTACT)).isEqualTo(41);

        mockMvc.perform(get("/players/{id}/season/stats", id).with(asOwner))
                .andExpect(status().isOk())
                .andExpect(view().name("season/stats"))
                .andExpect(content().string(Matchers.containsString("타격")))
                .andExpect(content().string(Matchers.containsString("1. 컨택 41")));
    }

    @Test
    void 투수도_시즌_화면을_볼_수_있다() throws Exception {
        Long id = drafted(Position.SP, List.of(PitchType.SLIDER, PitchType.CURVE, PitchType.CHANGEUP)).getId();

        mockMvc.perform(post("/players/{id}/season/play", id).with(csrf()).with(asOwner))
                .andExpect(redirectedUrl("/players/" + id + "/season/series/1"));
        mockMvc.perform(get("/players/{id}/season/series/1", id).with(asOwner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/players/{id}/season/stats", id).with(asOwner))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("투구")));
    }

    @Test
    void 다른_유저의_시즌은_볼_수_없다() throws Exception {
        Long id = drafted(Position.SS, List.of()).getId();
        User stranger = userRepository.save(new User("seasonstranger", "{noop}pw", "남의시즌"));

        mockMvc.perform(get("/players/{id}/season", id).with(user(new AuthUser(stranger))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/players/{id}/season/play", id).with(csrf()).with(user(new AuthUser(stranger))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 드래프트_전_선수는_시즌에_들어갈_수_없다() throws Exception {
        Player player = Player.create(owner.getId(), "신입", 1, Position.SS, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_EAST, 40, Map.of(), List.of());
        Long id = playerRepository.save(player).getId();

        mockMvc.perform(get("/players/{id}/season", id).with(asOwner))
                .andExpect(status().isConflict());
    }
}
