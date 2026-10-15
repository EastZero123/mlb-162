package com.mlbgame.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.auth.AuthUser;
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
import com.mlbgame.ranking.RankingService.Period;
import com.mlbgame.season.SeasonService;
import com.mlbgame.training.TrainingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class RankingIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RankingService rankingService;

    @Autowired
    RankingEntryRepository rankingRepository;

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

    private Player retiredPlayer(User owner, String name, int series) {
        Player player = Player.create(owner.getId(), name, 5, Position.SS, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(4), LeagueLevel.MLB);
        playerRepository.save(player);
        for (int i = 0; i < series; i++) {
            seasonService.playNextSeries(owner.getId(), player.getId());
            trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT);
        }
        careerService.retire(owner.getId(), player.getId());
        return player;
    }

    @Test
    void 은퇴하면_스냅샷이_한_번만_저장되고_누적_연봉_순으로_랭킹에_오른다() {
        User a = userRepository.save(new User("rankera", "{noop}pw", "랭커A"));
        User b = userRepository.save(new User("rankerb", "{noop}pw", "랭커B"));
        Player longCareer = retiredPlayer(a, "오래뛴", 3);
        Player shortCareer = retiredPlayer(b, "짧게뛴", 1);

        RankingEntry entry = rankingRepository.findByPlayerId(longCareer.getId()).orElseThrow();
        assertThat(entry.getNickname()).isEqualTo("랭커A");
        assertThat(entry.getCareerEarnings()).isPositive();
        assertThat(careerService.recordRetirement(longCareer).getId()).isEqualTo(entry.getId());   // 다시 저장하지 않음

        List<RankingService.Row> rows = rankingService.top(RankingCategory.EARNINGS, Period.ALL);
        assertThat(rows).extracting(r -> r.entry().getPlayerId()).containsSubsequence(longCareer.getId(), shortCareer.getId());
        assertThat(rows.get(0).rank()).isEqualTo(1);
        assertThat(rankingService.top(RankingCategory.EARNINGS, Period.MONTH)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void 비율_기록은_자격을_채운_선수만_오른다() {
        User a = userRepository.save(new User("rankerc", "{noop}pw", "랭커C"));
        retiredPlayer(a, "단기", 1);
        assertThat(rankingService.top(RankingCategory.AVG, Period.ALL)).isEmpty();   // 1,500타석 미만
        assertThat(rankingService.top(RankingCategory.ERA, Period.ALL)).isEmpty();
    }

    @Test
    void 월간은_이번_달_1일부터() {
        assertThat(RankingService.monthStart(LocalDate.of(2026, 10, 15))).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    @Test
    void 다른_유저도_은퇴_선수_프로필을_보고_현역_선수는_볼_수_없다() throws Exception {
        User owner = userRepository.save(new User("profowner", "{noop}pw", "주인"));
        User visitor = userRepository.save(new User("visitor", "{noop}pw", "손님"));
        Player retired = retiredPlayer(owner, "은퇴자", 1);

        mockMvc.perform(get("/profiles/{id}", retired.getId()).with(user(new AuthUser(visitor))))
                .andExpect(status().isOk())
                .andExpect(view().name("career/overview"))
                .andExpect(content().string(Matchers.containsString("능력치 (은퇴 시점)")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("은퇴하기"))));

        Player active = Player.create(owner.getId(), "현역", 6, Position.OF, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_EAST, 40, Map.of(), List.of());
        playerRepository.save(active);
        mockMvc.perform(get("/profiles/{id}", active.getId()).with(user(new AuthUser(visitor))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/rankings").with(user(new AuthUser(visitor))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("은퇴자")));
        mockMvc.perform(get("/rankings").param("category", "HOME_RUNS").param("period", "MONTH")
                        .with(user(new AuthUser(visitor))))
                .andExpect(status().isOk());
    }
}
