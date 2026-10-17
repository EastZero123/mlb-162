package com.mlbgame.season.transition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.stats.LeagueSeasonAverageRepository;
import com.mlbgame.league.world.LeagueWorldRepository;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.postseason.Postseason;
import com.mlbgame.postseason.PostseasonService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.PlayerSeasonRepository;
import com.mlbgame.season.SeasonService;
import com.mlbgame.simulation.rules.InjurySeverity;
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

/**
 * 시즌을 부상 상태로 마쳐도 시즌 전환(연봉 갱신 단계 진입) 때 부상이 모두 회복된다 (회귀 테스트).
 * 예전에는 새 시즌 행이 만들어지기 전까지 지난 시즌의 부상이 대시보드에 남아 있었다.
 * 정규시즌 54시리즈 + 포스트시즌을 실제로 진행하므로 시간이 조금 걸린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class InjuryRecoveryTransitionTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeasonService seasonService;

    @Autowired
    SeasonTransitionService transitionService;

    @Autowired
    PostseasonService postseasonService;

    @Autowired
    TrainingService trainingService;

    @Autowired
    PlayerSeasonRepository seasonRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    LeagueWorldRepository leagueWorldRepository;

    @Autowired
    LeagueSeasonAverageRepository leagueSeasonAverageRepository;

    @Test
    void 부상_중에_시즌을_마쳐도_시즌_전환_때_완전히_회복한다() throws Exception {
        User owner = userRepository.save(new User("hurtman", "{noop}pw", "부상병"));
        Player player = Player.create(owner.getId(), "김부상", 21, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(2), LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();

        Attribute[] cycle = {Attribute.CONTACT, Attribute.POWER, Attribute.EYE, Attribute.FIELDING};
        for (int i = 0; i < PlayerSeason.SERIES_PER_SEASON; i++) {
            seasonService.playNextSeries(owner.getId(), id);
            trainingService.train(owner.getId(), id, cycle[i % cycle.length]);
        }
        PlayerSeason closing = seasonService.currentSeason(owner.getId(), id);
        closing.injure(InjurySeverity.SEASON_ENDING.name(), PlayerSeason.SERIES_PER_SEASON);   // 시즌 아웃 부상으로 마감
        Postseason postseason = postseasonService.getOrStart(owner.getId(), id);
        for (int guard = 0; !postseason.isDone() && guard < 5; guard++) {
            postseason = postseasonService.playNextRound(owner.getId(), id);
        }

        SeasonTransitionService.Result result = transitionService.advance(owner.getId(), id);

        // 지난 시즌 행: 새 시즌 행이 생기기 전까지 대시보드가 이 행을 보여준다
        PlayerSeason last = seasonRepository.findByPlayerIdAndSeasonYear(id, result.fromYear()).orElseThrow();
        assertThat(last.isSidelined()).isFalse();
        assertThat(last.getInjuredSeriesRemaining()).isZero();
        assertThat(last.getInjurySeverity()).isNull();
        assertThat(result.headlines()).anyMatch(s -> s.contains("부상 회복"));
        // 시즌 전환 직전에 그해 리그 평균(세부 지표 기준)을 남긴다
        Long worldId = leagueWorldRepository.findByPlayerId(id).orElseThrow().getId();
        assertThat(leagueSeasonAverageRepository.findByWorldIdAndSeasonYear(worldId, result.fromYear())).isPresent();

        mockMvc.perform(get("/dashboard").with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString(">부상</span>"))));

        PlayerSeason next = seasonService.currentSeason(owner.getId(), id);
        assertThat(next.getSeasonYear()).isEqualTo(result.toYear());
        assertThat(next.isSidelined()).isFalse();
    }

    @Test
    void 부상_없이_시즌을_마치면_회복_소식이_없다() {
        User owner = userRepository.save(new User("healthy", "{noop}pw", "건강맨"));
        Player player = Player.create(owner.getId(), "박건강", 22, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(3), LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();

        Attribute[] cycle = {Attribute.CONTACT, Attribute.POWER, Attribute.EYE, Attribute.FIELDING};
        for (int i = 0; i < PlayerSeason.SERIES_PER_SEASON; i++) {
            seasonService.playNextSeries(owner.getId(), id);
            trainingService.train(owner.getId(), id, cycle[i % cycle.length]);
        }
        PlayerSeason closing = seasonService.currentSeason(owner.getId(), id);
        closing.recoverFromInjury();                       // 시뮬레이션 중 다친 경우를 배제
        Postseason postseason = postseasonService.getOrStart(owner.getId(), id);
        for (int guard = 0; !postseason.isDone() && guard < 5; guard++) {
            postseason = postseasonService.playNextRound(owner.getId(), id);
        }

        SeasonTransitionService.Result result = transitionService.advance(owner.getId(), id);

        assertThat(result.headlines()).noneMatch(s -> s.contains("부상 회복"));
    }
}
