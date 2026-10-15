package com.mlbgame.home;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.season.SeasonService;
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
class DashboardIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeasonService seasonService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Test
    void 대시보드_카드에_시즌_진행과_훈련_대기가_보인다() throws Exception {
        User owner = userRepository.save(new User("dashowner", "{noop}pw", "대시"));
        Player drafted = Player.create(owner.getId(), "지명선수", 5, Position.SS, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_EAST, 60, Map.of(), List.of());
        drafted.completeCombine();
        drafted.draft(teamRepository.findAllByOrderByIdAsc().get(3), LeagueLevel.MLB);
        playerRepository.save(drafted);
        seasonService.playNextSeries(owner.getId(), drafted.getId());

        Player created = Player.create(owner.getId(), "신인", 6, Position.OF, Hand.R, Hand.R, Nationality.USA,
                Education.HIGH_SCHOOL, SchoolRegion.US_WEST, 40, Map.of(), List.of());
        playerRepository.save(created);

        mockMvc.perform(get("/dashboard").with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("1/54")))
                .andExpect(content().string(Matchers.containsString("훈련 대기")))
                .andExpect(content().string(Matchers.containsString("season-progress-fill")))
                .andExpect(content().string(Matchers.containsString("컴바인 참가")));
    }
}
