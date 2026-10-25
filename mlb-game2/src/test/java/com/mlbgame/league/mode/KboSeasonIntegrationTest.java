package com.mlbgame.league.mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.league.world.NpcPlayer;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.NpcStatus;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.postseason.Bracket;
import com.mlbgame.postseason.Postseason;
import com.mlbgame.postseason.PostseasonService;
import com.mlbgame.season.SeasonService;
import com.mlbgame.season.SeasonService.SeriesOutcome;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.season.schedule.SeasonScheduleService.Matchup;
import com.mlbgame.season.transition.SeasonTransitionService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** K3: KBO 선수가 KBO 월드에서 시즌을 치른다 (구단·일정·2연전·NPC·연봉) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KboSeasonIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    SeasonService seasonService;

    @Autowired
    LeagueWorldService leagueWorldService;

    @Autowired
    NpcPlayerRepository npcPlayerRepository;

    @Autowired
    PostseasonService postseasonService;

    @Autowired
    SeasonTransitionService transitionService;

    User owner;
    List<Team> kboTeams;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("kboowner", "{noop}pw", "KBO주인"));
        kboTeams = teamRepository.findByLeagueModeOrderByIdAsc(LeagueMode.KBO);
    }

    private Player draftedKboShortstop() {
        Map<Attribute, Integer> allocation = new EnumMap<>(Attribute.class);
        Attribute.forPosition(Position.SS).forEach(a -> allocation.put(a, 5));
        Player player = Player.create(owner.getId(), "김유격", 7, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_WEST, 50, allocation, List.of(), LeagueMode.KBO);
        player.completeCombine();
        player.draft(kboTeams.get(0), LeagueLevel.FUTURES);
        return playerRepository.save(player);
    }

    @Test
    void KBO_구단은_10개() {
        assertThat(kboTeams).hasSize(10);
        assertThat(kboTeams).extracting(Team::getNameKo).contains("LG 트윈스", "KIA 타이거즈", "키움 히어로즈");
        assertThat(teamRepository.findByLeagueModeOrderByIdAsc(LeagueMode.MLB)).hasSize(30);
    }

    @Test
    void KBO_선수의_월드는_KBO_구단과_한국_선수_외국인_3명으로_만든다() {
        Player player = draftedKboShortstop();
        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());

        assertThat(world.getLeagueMode()).isEqualTo(LeagueMode.KBO);
        Set<Long> kboIds = kboTeams.stream().map(Team::getId).collect(Collectors.toSet());
        assertThat(leagueWorldService.teams(world.getId())).hasSize(10)
                .allSatisfy(t -> assertThat(kboIds).contains(t.getTeam().getId()));

        Map<Long, List<NpcPlayer>> rosters = npcPlayerRepository.findByWorldIdAndStatus(world.getId(), NpcStatus.MLB)
                .stream().collect(Collectors.groupingBy(NpcPlayer::getTeamId));
        assertThat(rosters).hasSize(10);
        for (List<NpcPlayer> roster : rosters.values()) {
            long foreign = roster.stream().filter(n -> n.getName().contains(" ")).count();   // 외국인: "이름 성"
            assertThat(foreign).isEqualTo(3);
            assertThat(roster.stream().filter(n -> !n.getName().contains(" ")))
                    .allSatisfy(n -> assertThat(n.getName()).matches("[가-힣]{3}"));
        }
    }

    @Test
    void 일정은_KBO_구단끼리_54시리즈이고_37번째부터_2연전() {
        Player player = draftedKboShortstop();
        List<Matchup> matchups = seasonService.matchups(owner.getId(), player.getId());

        assertThat(matchups).hasSize(54);
        assertThat(matchups).allSatisfy(m -> assertThat(m.opponent().getLeagueMode()).isEqualTo(LeagueMode.KBO));

        SeriesOutcome first = seasonService.playNextSeries(owner.getId(), player.getId());
        assertThat(first.result().games()).hasSize(3);
        assertThat(first.season().getLeagueLevel()).isIn(LeagueLevel.FUTURES, LeagueLevel.KBO);

        SeriesOutcome last = first;
        while (last.season().getNextSeriesNo() <= 37) {
            last.season().completeTraining();
            last = seasonService.playNextSeries(owner.getId(), player.getId());
        }
        assertThat(last.record().getSeriesNo()).isEqualTo(37);
        assertThat(last.result().games()).hasSize(2);
        assertThat(last.season().getTeamWins() + last.season().getTeamLosses()).isEqualTo(36 * 3 + 2);
    }

    @Test
    void 정규시즌_포스트시즌_다음_시즌까지_진행된다() {
        Player player = draftedKboShortstop();
        SeriesOutcome last = seasonService.playNextSeries(owner.getId(), player.getId());
        while (!last.season().isCompleted()) {
            last.season().completeTraining();
            last = seasonService.playNextSeries(owner.getId(), player.getId());
        }
        last.season().completeTraining();
        assertThat(last.season().getTeamWins() + last.season().getTeamLosses()).isEqualTo(144);

        Postseason postseason = postseasonService.getOrStart(owner.getId(), player.getId());
        Bracket bracket = postseasonService.bracket(postseason);
        assertThat(bracket.seeds).hasSize(5);
        while (!postseason.isDone()) {
            postseason = postseasonService.playNextRound(owner.getId(), player.getId());
        }
        bracket = postseasonService.bracket(postseason);
        assertThat(bracket.round(4)).hasSize(1);
        assertThat(postseason.getChampionTeamId()).isNotNull();

        SeasonTransitionService.Result next = transitionService.advance(owner.getId(), player.getId());
        assertThat(next.toYear()).isEqualTo(2027);
        assertThat(seasonService.matchups(owner.getId(), player.getId()))
                .allSatisfy(m -> assertThat(m.opponent().getLeagueMode()).isEqualTo(LeagueMode.KBO));
    }

    @Test
    void KBO_선수_화면은_KBO_용어와_한_표_순위로_그린다() throws Exception {
        Player player = draftedKboShortstop();
        seasonService.playNextSeries(owner.getId(), player.getId());
        AuthUser auth = new AuthUser(owner);

        mockMvc.perform(get("/players/" + player.getId() + "/league").with(user(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1군 순위")))
                .andExpect(content().string(containsString("한국시리즈 직행")))
                .andExpect(content().string(containsString("⚾ KBO 선수 키우기")));
        mockMvc.perform(get("/players/" + player.getId() + "/season").with(user(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("다음 승격 평가")));
        mockMvc.perform(get("/players/" + player.getId() + "/season/stats").with(user(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("퓨처스리그")))
                .andExpect(content().string(containsString("만원")));
        mockMvc.perform(get("/players/" + player.getId() + "/career").with(user(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1군 주요 시즌")));
        mockMvc.perform(get("/players/" + player.getId() + "/contract").with(user(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("고졸 8년")));
    }

    @Test
    void 퓨처스_연봉은_원화로_적립된다() {
        assertThat(EarningsService.salaryPerSeries(LeagueLevel.FUTURES)).isEqualTo(Math.round(30_000_000 / 54.0));
    }
}
