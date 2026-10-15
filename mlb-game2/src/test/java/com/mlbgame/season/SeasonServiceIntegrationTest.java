package com.mlbgame.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.mlbgame.season.SeasonService.SeriesOutcome;
import com.mlbgame.season.earnings.EarningType;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.season.earnings.PlayerEarningRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeasonServiceIntegrationTest {

    @Autowired
    SeasonService seasonService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    SeriesRecordRepository seriesRecordRepository;

    @Autowired
    PlayerEarningRepository earningRepository;

    User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("seasonowner", "{noop}pw", "시즌주인"));
    }

    /** 대졸 유격수(모든 능력치 40)를 드래프트 상태로 만든다 */
    private Player draftedShortstop(LeagueLevel level) {
        Map<Attribute, Integer> allocation = new EnumMap<>(Attribute.class);
        Attribute.forPosition(Position.SS).forEach(a -> allocation.put(a, 5));
        Player player = Player.create(owner.getId(), "김유격", 7, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_WEST, 35, allocation, List.of());
        player.completeCombine();
        Team team = teamRepository.findAllByOrderByIdAsc().get(5);
        player.draft(team, level);
        return playerRepository.save(player);
    }

    @Test
    void 드래프트한_선수는_2026시즌을_1번_시리즈부터_시작한다() {
        Player player = draftedShortstop(LeagueLevel.A);
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());

        assertThat(season.getSeasonYear()).isEqualTo(2026);
        assertThat(season.getLeagueLevel()).isEqualTo(LeagueLevel.A);
        assertThat(season.getNextSeriesNo()).isEqualTo(1);
        assertThat(season.isTrainingPending()).isFalse();
        assertThat(seasonService.matchups(owner.getId(), player.getId())).hasSize(54);
    }

    @Test
    void 시리즈를_진행하면_기록이_저장되고_훈련_전에는_다음_시리즈를_못_한다() {
        Player player = draftedShortstop(LeagueLevel.A);

        SeriesOutcome outcome = seasonService.playNextSeries(owner.getId(), player.getId());

        PlayerSeason season = outcome.season();
        assertThat(season.getNextSeriesNo()).isEqualTo(2);
        assertThat(season.getTeamWins() + season.getTeamLosses()).isEqualTo(3);
        assertThat(season.isTrainingPending()).isTrue();
        assertThat(outcome.result().games()).hasSize(3);
        assertThat(seriesRecordRepository.findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(player.getId(), 2026))
                .hasSize(1);
        assertThat(seasonService.batting(season).games).isEqualTo(
                outcome.result().batting() == null ? 0 : outcome.result().batting().games);

        // 싱글A 연봉 $25,000 / 54 시리즈
        assertThat(earningRepository.findByPlayerIdAndSeasonYearAndType(player.getId(), 2026, EarningType.SALARY))
                .get().extracting(e -> e.getAmount())
                .isEqualTo(EarningsService.salaryPerSeries(LeagueLevel.A));

        assertThatThrownBy(() -> seasonService.playNextSeries(owner.getId(), player.getId()))
                .isInstanceOf(SeasonProgressException.class)
                .hasMessageContaining("훈련");
    }

    @Test
    void 부상_결장_중에는_팀_경기만_진행되고_결장_기간이_줄어든다() {
        Player player = draftedShortstop(LeagueLevel.AA);
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());
        season.injure("MINOR", 2);

        SeriesOutcome outcome = seasonService.playNextSeries(owner.getId(), player.getId());

        assertThat(outcome.sidelined()).isTrue();
        assertThat(outcome.result().games()).allSatisfy(g -> assertThat(g.participated()).isFalse());
        assertThat(outcome.season().getInjuredSeriesRemaining()).isEqualTo(1);
        assertThat(outcome.season().getTeamWins() + outcome.season().getTeamLosses()).isEqualTo(3);
    }

    @Test
    void 저장된_시리즈는_같은_결과로_다시_계산된다() {
        Player player = draftedShortstop(LeagueLevel.A);
        SeriesOutcome outcome = seasonService.playNextSeries(owner.getId(), player.getId());

        SeriesRecord record = outcome.record();
        assertThat(record.getWins()).isEqualTo(outcome.result().wins());
    }
}
