package com.mlbgame.training;

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
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
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
class TrainingServiceIntegrationTest {

    @Autowired
    TrainingService trainingService;

    @Autowired
    SeasonService seasonService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("trainer", "{noop}pw", "훈련러"));
    }

    private Player drafted(int base, Map<Attribute, Integer> allocation) {
        Player player = Player.create(owner.getId(), "이훈련", 10, Position.OF, Hand.L, Hand.L, Nationality.KOREA,
                Education.HIGH_SCHOOL, SchoolRegion.OVERSEAS, base, allocation, List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(0), LeagueLevel.ROOKIE);
        return playerRepository.save(player);
    }

    @Test
    void 시리즈_뒤에_능력치_하나를_골라_1_올리면_다음_시리즈를_진행할_수_있다() {
        Player player = drafted(30, Map.of());
        seasonService.playNextSeries(owner.getId(), player.getId());

        TrainingLog log = trainingService.train(owner.getId(), player.getId(), Attribute.POWER);

        assertThat(log.getValueAfter()).isEqualTo(31);
        assertThat(log.getSeriesNo()).isEqualTo(1);
        assertThat(log.getSource()).isEqualTo(TrainingSource.MANUAL);
        assertThat(playerRepository.findById(player.getId()).orElseThrow().attribute(Attribute.POWER)).isEqualTo(31);

        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());
        assertThat(season.isTrainingPending()).isFalse();
        assertThat(seasonService.playNextSeries(owner.getId(), player.getId()).season().getNextSeriesNo()).isEqualTo(3);
    }

    @Test
    void 시리즈를_진행하기_전에는_훈련할_수_없다() {
        Player player = drafted(30, Map.of());
        assertThatThrownBy(() -> trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT))
                .isInstanceOf(SeasonProgressException.class);
    }

    @Test
    void 한_시리즈에_훈련은_한_번뿐이다() {
        Player player = drafted(30, Map.of());
        seasonService.playNextSeries(owner.getId(), player.getId());
        trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT);

        assertThatThrownBy(() -> trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT))
                .isInstanceOf(SeasonProgressException.class);
    }

    @Test
    void 포지션에_없거나_100인_능력치는_훈련할_수_없다() {
        Player player = drafted(75, Map.of(Attribute.SPEED, 25));   // 주력 100
        seasonService.playNextSeries(owner.getId(), player.getId());

        assertThatThrownBy(() -> trainingService.train(owner.getId(), player.getId(), Attribute.VELOCITY))
                .isInstanceOf(InvalidTrainingException.class);
        assertThatThrownBy(() -> trainingService.train(owner.getId(), player.getId(), Attribute.SPEED))
                .isInstanceOf(InvalidTrainingException.class);
        // 실패한 시도는 훈련 기회를 쓰지 않는다
        assertThat(seasonService.currentSeason(owner.getId(), player.getId()).isTrainingPending()).isTrue();
    }

    @Test
    void 자동_훈련_순서는_돌아가며_고르고_최대치는_건너뛴다() {
        Player player = drafted(75, Map.of(Attribute.CONTACT, 25));   // 컨택 100
        List<Attribute> order = List.of(Attribute.CONTACT, Attribute.POWER, Attribute.EYE);

        assertThat(AutoTrainingOrder.choose(player, order, 0)).isEqualTo(Attribute.POWER);   // 컨택은 100 → 건너뜀
        assertThat(AutoTrainingOrder.choose(player, order, 2)).isEqualTo(Attribute.EYE);

        seasonService.playNextSeries(owner.getId(), player.getId());
        TrainingLog log = trainingService.trainAutomatically(owner.getId(), player.getId(), order);
        assertThat(log.getSource()).isEqualTo(TrainingSource.AUTO);
        assertThat(log.getAttribute()).isEqualTo(Attribute.POWER);
    }
}
