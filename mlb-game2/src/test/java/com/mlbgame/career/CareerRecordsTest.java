package com.mlbgame.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.mlbgame.postseason.Postseason;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonService;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.training.TrainingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 월드시리즈 우승 판정·명예의 전당 가산점, 커리어 화면의 기록표(메이저·마이너·수비·포스트시즌) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class CareerRecordsTest {

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

    @Autowired
    JdbcTemplate jdbc;

    // ------------------------------------------------------------ 우승 판정 (순수 계산)

    private static Postseason postseason(int year, boolean done, Long champion) {
        Postseason p = mock(Postseason.class);
        when(p.getSeasonYear()).thenReturn(year);
        when(p.isDone()).thenReturn(done);
        when(p.getChampionTeamId()).thenReturn(champion);
        return p;
    }

    @Test
    void 메이저_소속으로_시즌을_마친_팀이_우승해야_우승으로_인정한다() {
        Map<Integer, PlayerSeason> seasons = Map.of(
                2027, PlayerSeason.start(1L, 2027, LeagueLevel.MLB, 5L),    // 메이저 · 우승팀 → 인정
                2028, PlayerSeason.start(1L, 2028, LeagueLevel.AAA, 5L),    // 같은 구단이지만 마이너 → 불인정
                2029, PlayerSeason.start(1L, 2029, LeagueLevel.MLB, 7L),    // 트레이드돼 다른 팀으로 마침 → 불인정
                2030, PlayerSeason.start(1L, 2030, LeagueLevel.MLB, 5L));   // 포스트시즌 진행 중 → 불인정
        List<Postseason> postseasons = List.of(
                postseason(2027, true, 5L),
                postseason(2028, true, 5L),
                postseason(2029, true, 5L),
                postseason(2030, false, null));

        assertThat(CareerService.championships(postseasons, seasons)).containsOnlyKeys(2027).containsEntry(2027, 5L);
    }

    @Test
    void 우승_1회당_명예의_전당_점수_5점() {
        BattingLine batting = new BattingLine();
        PitchingLine pitching = new PitchingLine();
        int none = CareerService.hallOfFameScore(false, batting, pitching, 0, 0, 0);
        int two = CareerService.hallOfFameScore(false, batting, pitching, 0, 0, 2);
        assertThat(two - none).isEqualTo(2 * CareerService.CHAMPIONSHIP_POINTS).isEqualTo(10);
    }

    // ------------------------------------------------------------ 커리어 화면

    @Test
    void 커리어_화면에_메이저_수비_포스트시즌_기록과_우승_배너가_나온다() throws Exception {
        User owner = userRepository.save(new User("ringman", "{noop}pw", "반지"));
        Player player = Player.create(owner.getId(), "반지왕", 3, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(6), LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();
        for (int i = 0; i < 2; i++) {
            seasonService.playNextSeries(owner.getId(), id);
            trainingService.train(owner.getId(), id, Attribute.CONTACT);
        }
        PlayerSeason season = seasonService.currentSeason(owner.getId(), id);
        int scoreBefore = careerService.summary(owner.getId(), id).hallOfFameScore();

        // 그해 포스트시즌: 내 팀 우승, 내 선수 4경기 출전
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("insert into postseasons (player_id, season_year, status, next_round, user_team_qualified, user_plays,"
                        + " bracket_json, batting_json, fatigue, games_since_start, consecutive_days, last_pitches,"
                        + " games_since_appearance, injured, champion_team_id, version, created_at, updated_at)"
                        + " values (?, ?, 'DONE', 5, true, true, '{}', ?, 0, 0, 0, 0, 0, false, ?, 0, ?, ?)",
                id, season.getSeasonYear(), "{\"games\":4,\"plateAppearances\":16,\"atBats\":15,\"hits\":6}",
                season.getTeamId(), now, now);

        CareerService.Summary summary = careerService.summary(owner.getId(), id);
        assertThat(summary.championshipYears()).containsExactly(season.getSeasonYear());
        assertThat(summary.hallOfFameScore()).isEqualTo(scoreBefore + CareerService.CHAMPIONSHIP_POINTS);
        assertThat(summary.records().mlb()).isNotEmpty();
        assertThat(summary.postseason()).hasSize(2);                     // 그해 + 통산
        assertThat(summary.postseason().get(0).batting().hits).isEqualTo(6);

        mockMvc.perform(get("/players/{id}/career", id).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("월드시리즈 우승 1회")))
                .andExpect(content().string(Matchers.containsString(section("정규시즌 기록"))))
                .andExpect(content().string(Matchers.containsString("<h3 class=\"sub-title\">메이저리그</h3>")))
                .andExpect(content().string(Matchers.containsString(section("수비 기록"))))
                .andExpect(content().string(Matchers.containsString(section("포스트시즌 기록"))))
                .andExpect(content().string(Matchers.containsString(section("세부 지표"))))
                .andExpect(content().string(Matchers.containsString("메이저 통산 WAR")))
                .andExpect(content().string(Matchers.matchesRegex("(?s).*<td class=\"left\">\\s*통산\\s*</td>.*")));
    }

    /** 섹션 제목 태그 — 같은 글자가 HTML 주석에도 있어서 글자만으로는 표가 있는지 알 수 없다 */
    private static String section(String title) {
        return "<h2 class=\"section-title\">" + title + "</h2>";
    }

    @Test
    void 투수_커리어_화면에는_수비_기록_표가_없다() throws Exception {
        User owner = userRepository.save(new User("armman", "{noop}pw", "투수"));
        Player player = Player.create(owner.getId(), "김투수", 41, Position.SP, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(),
                List.of(PitchType.SLIDER, PitchType.CURVE, PitchType.CHANGEUP));
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(7), LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();
        seasonService.playNextSeries(owner.getId(), id);

        mockMvc.perform(get("/players/{id}/career", id).with(user(new AuthUser(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(section("정규시즌 기록"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString(section("수비 기록")))));
    }
}
