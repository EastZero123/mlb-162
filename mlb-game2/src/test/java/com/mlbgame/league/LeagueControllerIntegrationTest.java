package com.mlbgame.league;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.season.SeasonService;
import com.mlbgame.training.TrainingService;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
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
class LeagueControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    SeasonService seasonService;

    @Autowired
    TrainingService trainingService;

    @Test
    void 메이저리그_선수는_한_달이_지나면_순위표와_리더보드를_본다() throws Exception {
        User owner = userRepository.save(new User("leaguer", "{noop}pw", "리그러"));
        Player player = Player.create(owner.getId(), "정메이저", 99, Position.OF, Hand.L, Hand.L, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_WEST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(27), LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();

        mockMvc.perform(get("/players/{id}/league", id).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(view().name("league/overview"))
                .andExpect(content().string(Matchers.containsString("첫 달")));

        for (int i = 0; i < 9; i++) {
            seasonService.playNextSeries(owner.getId(), id);
            trainingService.train(owner.getId(), id, Attribute.POWER);
        }

        mockMvc.perform(get("/players/{id}/league", id).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("아메리칸리그")))
                .andExpect(content().string(Matchers.containsString("홈런")))
                .andExpect(content().string(Matchers.containsString("와일드카드")));
    }
}
