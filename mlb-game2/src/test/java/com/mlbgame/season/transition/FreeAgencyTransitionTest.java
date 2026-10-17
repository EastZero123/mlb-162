package com.mlbgame.season.transition;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.contract.ContractOffer;
import com.mlbgame.contract.ContractRules;
import com.mlbgame.contract.ContractService;
import com.mlbgame.contract.ContractType;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.Payroll;
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
import com.mlbgame.training.TrainingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 시즌 전환 흐름에서 FA 오퍼가 여러 구단에서 오는지 (회귀 테스트).
 * 예전에는 NPC FA 시장이 먼저 열려 구단 예산이 바닥난 뒤 내 선수 오퍼를 만들어, 원소속팀 1년 계약만 남았다.
 * 정규시즌 54시리즈 + 포스트시즌을 실제로 진행하므로 시간이 조금 걸린다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class FreeAgencyTransitionTest {

    @Autowired
    SeasonService seasonService;

    @Autowired
    SeasonTransitionService transitionService;

    @Autowired
    PostseasonService postseasonService;

    @Autowired
    TrainingService trainingService;

    @Autowired
    ContractService contractService;

    @Autowired
    PlayerSeasonRepository seasonRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    @Test
    void 시즌_전환_때_FA_자격을_얻으면_여러_구단이_오퍼를_보낸다() {
        User owner = userRepository.save(new User("freeagent", "{noop}pw", "FA"));
        Player player = Player.create(owner.getId(), "나에프", 11, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 80, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(4), LeagueLevel.MLB);
        player.signContract(null, 10_000_000, 1, ContractType.ARBITRATION);    // 올 시즌으로 계약 만료
        Long id = playerRepository.save(player).getId();
        Long originalTeam = player.getTeam().getId();
        for (int i = 0; i < Payroll.FREE_AGENCY_SERVICE; i++) {                 // 지난 6년 서비스
            PlayerSeason past = PlayerSeason.start(id, 2000 + i, LeagueLevel.MLB, originalTeam);
            ReflectionTestUtils.setField(past, "mlbSeriesPlayed", ContractRules.SERIES_PER_SERVICE_YEAR);
            seasonRepository.save(past);
        }

        Attribute[] cycle = {Attribute.CONTACT, Attribute.POWER, Attribute.EYE, Attribute.FIELDING};
        for (int i = 0; i < PlayerSeason.SERIES_PER_SEASON; i++) {
            seasonService.playNextSeries(owner.getId(), id);
            trainingService.train(owner.getId(), id, cycle[i % cycle.length]);
        }
        Postseason postseason = postseasonService.getOrStart(owner.getId(), id);
        for (int guard = 0; !postseason.isDone() && guard < 5; guard++) {
            postseason = postseasonService.playNextRound(owner.getId(), id);
        }

        SeasonTransitionService.Result result = transitionService.advance(owner.getId(), id);

        List<ContractOffer> offers = contractService.pending(id);
        assertThat(result.headlines()).anyMatch(s -> s.contains("FA 자격 취득"));
        assertThat(offers).hasSizeGreaterThanOrEqualTo(3);                    // 예전: 원소속팀 1년 1건
        assertThat(offers).extracting(o -> o.getTeam().getId()).doesNotHaveDuplicates()
                .anyMatch(teamId -> !teamId.equals(originalTeam));
        assertThat(offers).anyMatch(o -> o.getYears() > 1);
    }
}
