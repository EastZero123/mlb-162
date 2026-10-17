package com.mlbgame.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.season.SeasonSplits.Kind;
import com.mlbgame.season.SeasonSplits.Row;
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

/** 시리즈를 뛸 때 그 순간 소속 구단이 저장되고, 시즌 중 트레이드되면 시즌 기록 화면이 팀별 + 합계로 나뉜다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class SeriesTeamRecordingTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeasonService seasonService;

    @Autowired
    SeasonSplits seasonSplits;

    @Autowired
    TrainingService trainingService;

    @Autowired
    SeriesRecordRepository seriesRecordRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Test
    void 시즌_중_트레이드되면_시리즈마다_뛴_구단이_남고_시즌_기록이_팀별로_나뉜다() throws Exception {
        List<Team> teams = teamRepository.findAllByOrderByIdAsc();
        Team before = teams.get(8);
        Team after = teams.get(9);
        User owner = userRepository.save(new User("splitman", "{noop}pw", "분할"));
        Player player = Player.create(owner.getId(), "이분할", 8, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 75, Map.of(), List.of());
        player.completeCombine();
        player.draft(before, LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();

        play(owner, id);
        PlayerSeason season = seasonService.currentSeason(owner.getId(), id);
        player.tradeTo(after);                       // 트레이드 (TradeService.execute 와 같은 처리)
        season.trade(after.getId());
        play(owner, id);

        assertThat(seriesRecordRepository.findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(id, season.getSeasonYear()))
                .extracting(SeriesRecord::getTeamId)
                .containsExactly(before.getId(), after.getId());

        List<Row> mlb = seasonSplits.season(id, season.getSeasonYear()).mlb();
        if (mlb.size() == 3) {                       // 두 시리즈 모두 출전했을 때 (부상으로 빠지면 한 줄)
            assertThat(mlb).extracting(Row::kind).containsExactly(Kind.SPLIT, Kind.SPLIT, Kind.YEAR_TOTAL);
            assertThat(mlb).noneMatch(r -> r.kind() == Kind.CAREER_TOTAL);   // 시즌 화면에는 통산 없음
            mockMvc.perform(get("/players/{id}/season/stats", id).with(user(new AuthUser(owner))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("합계 (2팀)")))
                    .andExpect(content().string(Matchers.containsString("<h2 class=\"section-title\">세부 지표</h2>")));
        }
    }

    private void play(User owner, Long id) {
        seasonService.playNextSeries(owner.getId(), id);
        trainingService.train(owner.getId(), id, Attribute.CONTACT);
    }
}
