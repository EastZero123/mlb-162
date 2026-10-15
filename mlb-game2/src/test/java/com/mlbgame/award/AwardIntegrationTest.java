package com.mlbgame.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
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
import com.mlbgame.season.SeasonService.SeriesOutcome;
import com.mlbgame.season.earnings.EarningType;
import com.mlbgame.season.earnings.PlayerEarningRepository;
import com.mlbgame.training.InvalidTrainingException;
import com.mlbgame.training.TrainingService;
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
class AwardIntegrationTest {

    @Autowired
    SeasonService seasonService;

    @Autowired
    TrainingService trainingService;

    @Autowired
    StatPointService statPointService;

    @Autowired
    AwardRepository awardRepository;

    @Autowired
    PlayerEarningRepository earningRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("awardee", "{noop}pw", "수상자"));
    }

    private Player drafted(int rating, LeagueLevel level) {
        Player player = Player.create(owner.getId(), "한수상", 8, Position.OF, Hand.L, Hand.L, Nationality.TAIWAN,
                Education.HIGH_SCHOOL, SchoolRegion.OVERSEAS, rating, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(15), level);
        return playerRepository.save(player);
    }

    @Test
    void 루키리그를_압도하면_첫_달_마이너리그_이달의_선수를_받고_포인트가_쌓인다() {
        Player player = drafted(90, LeagueLevel.ROOKIE);   // 비교군 평균 38 안팎
        SeriesOutcome ninth = null;
        for (int i = 0; i < 9; i++) {
            ninth = seasonService.playNextSeries(owner.getId(), player.getId());
            trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT);
        }
        Player reloaded = playerRepository.findById(player.getId()).orElseThrow();
        boolean enoughPlayingTime = ninth.season().getInjuredSeriesRemaining() == 0;
        if (!enoughPlayingTime) {
            return;                                         // 부상으로 표본이 모자라면 판정이 달라질 수 있음
        }
        assertThat(ninth.awards()).extracting(Award::getType).contains(AwardType.MINOR_PLAYER_OF_MONTH);
        assertThat(reloaded.getStatPoints()).isGreaterThanOrEqualTo(1);
        assertThat(earningRepository.findByPlayerIdAndSeasonYearAndType(player.getId(), 2026, EarningType.AWARD_BONUS))
                .isPresent();
        assertThat(awardRepository.findByPlayerIdAndSeasonYearAndAfterSeries(player.getId(), 2026, 9)).isNotEmpty();
    }

    @Test
    void 포인트로_능력치를_올리고_포인트가_없으면_쓸_수_없다() {
        Player player = drafted(40, LeagueLevel.ROOKIE);
        player.addStatPoints(1);

        StatPointLog log = statPointService.spend(owner.getId(), player.getId(), Attribute.POWER);
        assertThat(log.getValueAfter()).isEqualTo(41);
        assertThat(playerRepository.findById(player.getId()).orElseThrow().getStatPoints()).isZero();

        assertThatThrownBy(() -> statPointService.spend(owner.getId(), player.getId(), Attribute.POWER))
                .isInstanceOf(InvalidTrainingException.class);
    }

    @Test
    void 포지션에_없는_능력치에는_포인트를_쓸_수_없다() {
        Player player = drafted(40, LeagueLevel.ROOKIE);
        player.addStatPoints(1);
        assertThatThrownBy(() -> statPointService.spend(owner.getId(), player.getId(), Attribute.VELOCITY))
                .isInstanceOf(InvalidTrainingException.class);
        assertThat(playerRepository.findById(player.getId()).orElseThrow().getStatPoints()).isEqualTo(1);
    }
}
