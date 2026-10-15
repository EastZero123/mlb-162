package com.mlbgame.postseason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldRepository;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.NpcStatus;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.transition.SeasonTransitionService;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
import com.mlbgame.training.TrainingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 정규시즌 54시리즈를 실제로 끝까지 진행한 뒤 포스트시즌을 진행한다 (시간이 조금 걸리는 테스트) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PostseasonIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostseasonService postseasonService;

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

    @Autowired
    SeasonTransitionService transitionService;

    @Autowired
    LeagueWorldRepository leagueWorldRepository;

    @Autowired
    NpcPlayerRepository npcPlayerRepository;

    @Test
    void 정규시즌이_끝나야_포스트시즌이_열리고_끝까지_진행하면_우승팀이_나온다() throws Exception {
        User owner = userRepository.save(new User("octoberman", "{noop}pw", "가을남자"));
        Player player = Player.create(owner.getId(), "오가을", 10, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(0), LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();

        assertThatThrownBy(() -> postseasonService.getOrStart(owner.getId(), id))
                .isInstanceOf(SeasonProgressException.class);

        Attribute[] cycle = {Attribute.CONTACT, Attribute.POWER, Attribute.EYE, Attribute.FIELDING};
        for (int i = 0; i < 54; i++) {
            seasonService.playNextSeries(owner.getId(), id);
            trainingService.train(owner.getId(), id, cycle[i % cycle.length]);
        }

        Postseason postseason = postseasonService.getOrStart(owner.getId(), id);
        int guard = 0;
        while (!postseason.isDone() && guard++ < 5) {
            postseason = postseasonService.playNextRound(owner.getId(), id);
        }
        assertThat(postseason.isDone()).isTrue();
        assertThat(postseason.getChampionTeamId()).isNotNull();
        Bracket bracket = postseasonService.bracket(postseason);
        assertThat(bracket.round(4)).hasSize(1);
        if (postseason.isUserTeamQualified()) {
            assertThat(bracket.series).anyMatch(s -> s.userTeam);
        }

        mockMvc.perform(get("/players/{id}/postseason", id).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(view().name("postseason/overview"));

        // 시즌 전환: 2027 시즌, 나이 +1, 리그 월드는 구단마다 1군 26명 유지
        int ageBefore = playerRepository.findById(id).orElseThrow().getAge();
        SeasonTransitionService.Result result = transitionService.advance(owner.getId(), id);
        assertThat(result.toYear()).isEqualTo(2027);
        assertThat(result.newAge()).isEqualTo(ageBefore + 1);
        assertThat(result.retired()).isFalse();
        assertThat(result.headlines()).isNotEmpty();

        LeagueWorld world = leagueWorldRepository.findByPlayerId(id).orElseThrow();
        assertThat(world.getCurrentYear()).isEqualTo(2027);
        assertThat(npcPlayerRepository.findByWorldIdAndStatus(world.getId(), NpcStatus.MLB)).hasSize(30 * 26);

        PlayerSeason next = seasonService.currentSeason(owner.getId(), id);
        assertThat(next.getSeasonYear()).isEqualTo(2027);
        assertThat(next.getNextSeriesNo()).isEqualTo(1);
        assertThat(seasonService.playNextSeries(owner.getId(), id).record().getSeasonYear()).isEqualTo(2027);
    }

    @Test
    void 정규시즌과_포스트시즌을_마치기_전에는_다음_시즌으로_넘어갈_수_없다() {
        User owner = userRepository.save(new User("tooearly", "{noop}pw", "성급이"));
        Player player = Player.create(owner.getId(), "급한", 1, Position.SS, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_EAST, 50, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(1), LeagueLevel.A);
        Long id = playerRepository.save(player).getId();

        assertThatThrownBy(() -> transitionService.advance(owner.getId(), id))
                .isInstanceOf(SeasonProgressException.class);
    }
}
