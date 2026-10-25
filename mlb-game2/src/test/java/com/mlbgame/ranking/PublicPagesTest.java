package com.mlbgame.ranking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.career.CareerService;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 로그인하지 않은 방문자도 랭킹·은퇴 선수 프로필·패치노트를 볼 수 있다. 게임 화면은 여전히 로그인 필요 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class PublicPagesTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CareerService careerService;

    @Autowired
    SeasonService seasonService;

    @Autowired
    TrainingService trainingService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Test
    void 비로그인_방문자도_랭킹을_볼_수_있다() throws Exception {
        mockMvc.perform(get("/rankings"))
                .andExpect(status().isOk())
                .andExpect(view().name("ranking/list"))
                .andExpect(content().string(Matchers.containsString("href=\"/login\"")));   // 헤더에 로그인 버튼
        mockMvc.perform(get("/rankings").param("category", "HOME_RUNS").param("period", "MONTH"))
                .andExpect(status().isOk());
    }

    @Test
    void 비로그인_방문자도_은퇴_선수_프로필을_볼_수_있다() throws Exception {
        User owner = userRepository.save(new User("pubowner", "{noop}pw", "공개주인"));
        Player player = Player.create(owner.getId(), "공개은퇴", 5, Position.SS, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(4), LeagueLevel.MLB);
        playerRepository.save(player);
        seasonService.playNextSeries(owner.getId(), player.getId());
        trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT);
        careerService.retire(owner.getId(), player.getId());

        mockMvc.perform(get("/rankings"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("공개은퇴")));
        mockMvc.perform(get("/profiles/{id}", player.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("career/overview"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("은퇴하기"))));
    }

    @Test
    void 비로그인_방문자도_현역_선수_프로필은_볼_수_없다() throws Exception {
        User owner = userRepository.save(new User("pubactive", "{noop}pw", "현역주인"));
        Player active = playerRepository.save(Player.create(owner.getId(), "현역", 6, Position.OF, Hand.R, Hand.R,
                Nationality.USA, Education.COLLEGE, SchoolRegion.US_EAST, 40, Map.of(), List.of()));

        mockMvc.perform(get("/profiles/{id}", active.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비로그인_방문자도_패치노트를_볼_수_있다() throws Exception {
        mockMvc.perform(get("/patch-notes"))
                .andExpect(status().isOk())
                .andExpect(view().name("patch-notes"))
                .andExpect(content().string(Matchers.containsString("패치노트")));
    }

    @Test
    void 패치노트는_MLB_KBO_통합이고_모드별로_걸러_볼_수_있다() throws Exception {
        mockMvc.perform(get("/patch-notes"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("v1.0.0")))
                .andExpect(content().string(Matchers.containsString("data-patch-filter=\"kbo\"")))
                .andExpect(content().string(Matchers.containsString("<span class=\"mode-tag kbo\">KBO</span>")));
        mockMvc.perform(get("/patch-notes").cookie(new jakarta.servlet.http.Cookie("lang", "en")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("v1.0.0")))
                .andExpect(content().string(Matchers.containsString("KBO mode launch")));
    }

    @Test
    void 로그인_화면에_랭킹과_패치노트_링크가_있다() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/rankings")))
                .andExpect(content().string(Matchers.containsString("/patch-notes")));
    }

    @Test
    void 게임_화면은_여전히_로그인이_필요하다() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
