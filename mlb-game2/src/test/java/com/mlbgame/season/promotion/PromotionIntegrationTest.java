package com.mlbgame.season.promotion;

import static org.assertj.core.api.Assertions.assertThat;

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
class PromotionIntegrationTest {

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
    LevelChangeRepository levelChangeRepository;

    User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("promoter", "{noop}pw", "승격러"));
    }

    private Player drafted(int rating, LeagueLevel level) {
        Player player = Player.create(owner.getId(), "최승격", 3, Position.B2, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_SOUTH, rating, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(10), level);
        return playerRepository.save(player);
    }

    /** 시리즈 진행 + 훈련을 n번 반복하고 마지막 결과를 돌려준다 */
    private SeriesOutcome playSeries(Player player, int count) {
        SeriesOutcome outcome = null;
        for (int i = 0; i < count; i++) {
            outcome = seasonService.playNextSeries(owner.getId(), player.getId());
            trainingService.train(owner.getId(), player.getId(), Attribute.CONTACT);
        }
        return outcome;
    }

    @Test
    void 루키리그에서_압도적인_선수는_9시리즈_뒤_싱글A로_승격한다() {
        Player player = drafted(70, LeagueLevel.ROOKIE);   // 루키 평균 35 보다 35 높음

        SeriesOutcome eighth = playSeries(player, 8);
        assertThat(eighth.levelChange()).isNull();

        SeriesOutcome ninth = playSeries(player, 1);
        if (ninth.season().isSidelined()) {
            return;                                        // 9번째 시리즈에 부상이 나면 판정을 건너뛰므로 종료
        }
        assertThat(ninth.levelChange()).isNotNull();
        assertThat(ninth.levelChange().getToLevel()).isEqualTo(LeagueLevel.A);
        assertThat(ninth.season().getLeagueLevel()).isEqualTo(LeagueLevel.A);
        assertThat(playerRepository.findById(player.getId()).orElseThrow().getLeagueLevel()).isEqualTo(LeagueLevel.A);
        assertThat(levelChangeRepository.findByPlayerIdOrderByIdAsc(player.getId())).hasSize(1);
    }

    @Test
    void 메이저에서_한참_모자라면_트리플A로_강등되고_메이저_시리즈_수가_쌓인다() {
        Player player = drafted(40, LeagueLevel.MLB);       // 메이저 평균 65 보다 25 낮음

        SeriesOutcome ninth = playSeries(player, 9);
        assertThat(ninth.season().getMlbSeriesPlayed()).isEqualTo(9);
        if (ninth.season().isSidelined()) {
            return;
        }
        assertThat(ninth.levelChange()).isNotNull();
        assertThat(ninth.levelChange().getToLevel()).isEqualTo(LeagueLevel.AAA);
        assertThat(ninth.levelChange().getMessage()).isEqualTo("트리플A 강등");
    }
}
