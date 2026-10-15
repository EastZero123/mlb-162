package com.mlbgame.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.contract.ContractOffer.Kind;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.league.world.Payroll;
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
import com.mlbgame.training.InvalidTrainingException;
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
class ContractServiceIntegrationTest {

    @Autowired
    ContractService contractService;

    @Autowired
    ContractOfferRepository offerRepository;

    @Autowired
    SeasonService seasonService;

    @Autowired
    LeagueWorldService leagueWorldService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    User owner;
    List<Team> teams;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("agent", "{noop}pw", "에이전트"));
        teams = teamRepository.findAllByOrderByIdAsc();
    }

    private Player mlbPlayer(int rating) {
        Player player = Player.create(owner.getId(), "강계약", 30, Position.OF, Hand.R, Hand.R, Nationality.USA,
                Education.COLLEGE, SchoolRegion.US_WEST, rating, Map.of(), List.of());
        player.completeCombine();
        player.draft(teams.get(3), LeagueLevel.MLB);
        return playerRepository.save(player);
    }

    private ContractOffer offer(Player player, Kind kind, Team team, int years, long salary, long fair) {
        return offerRepository.save(new ContractOffer(player.getId(), 2027, kind, team, years, salary, fair));
    }

    @Test
    void 메이저에_올라오면_최저_연봉_계약이_생기고_마이너는_레벨_연봉을_받는다() {
        Player player = mlbPlayer(70);
        assertThat(player.getContractType()).isEqualTo(ContractType.NONE);
        assertThat(ContractService.seriesSalary(player, LeagueLevel.AA)).isEqualTo(Math.round(30_000 / 54.0));

        contractService.ensureMlbContract(player, LeagueLevel.MLB);
        assertThat(player.getContractType()).isEqualTo(ContractType.PRE_ARB);
        assertThat(player.getSalary()).isEqualTo(Payroll.MINIMUM_SALARY);
        assertThat(ContractService.seriesSalary(player, LeagueLevel.MLB)).isEqualTo(Math.round(760_000 / 54.0));
    }

    @Test
    void 보장_계약은_마이너로_내려가도_계약_연봉을_받는다() {
        Player player = mlbPlayer(70);
        player.signContract(null, 10_800_000, 3, ContractType.FREE_AGENT);
        assertThat(ContractService.seriesSalary(player, LeagueLevel.AAA)).isEqualTo(200_000);
    }

    @Test
    void 서비스_3년_미만은_오프시즌에_최저_연봉으로_재계약된다() {
        Player player = mlbPlayer(70);
        contractService.ensureMlbContract(player, LeagueLevel.MLB);
        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());

        List<String> news = contractService.offseason(player, world, 2026);
        assertThat(news).anyMatch(s -> s.contains("최저 연봉"));
        assertThat(player.getContractType()).isEqualTo(ContractType.PRE_ARB);
        assertThat(contractService.hasPending(player.getId())).isFalse();
    }

    @Test
    void 다년_계약은_1년씩_줄어든다() {
        Player player = mlbPlayer(70);
        player.signContract(null, 15_000_000, 3, ContractType.EXTENSION);
        LeagueWorld world = leagueWorldService.getOrCreate(player.getId());

        List<String> news = contractService.offseason(player, world, 2026);
        assertThat(player.getContractYears()).isEqualTo(2);
        assertThat(news).anyMatch(s -> s.contains("2년 남음"));
    }

    @Test
    void 연봉조정_청문회는_적정_연봉에_가까운_쪽으로_결정된다() {
        Player player = mlbPlayer(70);
        ContractOffer arb = offer(player, Kind.ARBITRATION, player.getTeam(), 1, 3_000_000, 4_000_000);

        contractService.arbitrate(owner.getId(), player.getId(), arb.getId(), 4_200_000);
        assertThat(player.getSalary()).isEqualTo(4_200_000);
        assertThat(player.getContractType()).isEqualTo(ContractType.ARBITRATION);

        ContractOffer greedy = offer(player, Kind.ARBITRATION, player.getTeam(), 1, 3_000_000, 4_000_000);
        contractService.arbitrate(owner.getId(), player.getId(), greedy.getId(), 9_000_000);
        assertThat(player.getSalary()).isEqualTo(3_000_000);
    }

    @Test
    void 연봉조정은_거절할_수_없고_구단_제시액보다_낮게_요구할_수_없다() {
        Player player = mlbPlayer(70);
        ContractOffer arb = offer(player, Kind.ARBITRATION, player.getTeam(), 1, 3_000_000, 4_000_000);
        assertThatThrownBy(() -> contractService.decline(owner.getId(), player.getId(), arb.getId()))
                .isInstanceOf(InvalidTrainingException.class);
        assertThatThrownBy(() -> contractService.arbitrate(owner.getId(), player.getId(), arb.getId(), 2_000_000))
                .isInstanceOf(InvalidTrainingException.class);
    }

    @Test
    void FA_오퍼를_수락하면_구단이_바뀌고_다른_오퍼는_소멸한다() {
        Player player = mlbPlayer(80);
        Team newTeam = teams.get(20);
        ContractOffer stay = offer(player, Kind.FREE_AGENCY, player.getTeam(), 3, 20_000_000, 20_000_000);
        ContractOffer move = offer(player, Kind.FREE_AGENCY, newTeam, 5, 25_000_000, 20_000_000);

        contractService.accept(owner.getId(), player.getId(), move.getId());

        assertThat(player.getTeam().getId()).isEqualTo(newTeam.getId());
        assertThat(player.getContractType()).isEqualTo(ContractType.FREE_AGENT);
        assertThat(player.getContractYears()).isEqualTo(5);
        assertThat(offerRepository.findById(stay.getId()).orElseThrow().getStatus())
                .isEqualTo(ContractOffer.Status.REJECTED);
        assertThat(contractService.hasPending(player.getId())).isFalse();
    }

    @Test
    void FA_역제안이_결렬되고_오퍼가_없으면_원소속팀이_저가_제안을_한다() {
        Player player = mlbPlayer(80);
        ContractOffer only = offer(player, Kind.FREE_AGENCY, teams.get(7), 3, 20_000_000, 20_000_000);

        contractService.counter(owner.getId(), player.getId(), only.getId(), 30_000_000);   // 50% 초과 → 결렬

        List<ContractOffer> pending = contractService.pending(player.getId());
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getTeam().getId()).isEqualTo(player.getTeam().getId());
        assertThat(pending.get(0).getYears()).isEqualTo(1);
    }

    @Test
    void 오퍼보다_낮거나_같은_역제안은_바로_수락된다() {
        Player player = mlbPlayer(80);
        ContractOffer o = offer(player, Kind.FREE_AGENCY, teams.get(7), 3, 20_000_000, 20_000_000);
        contractService.counter(owner.getId(), player.getId(), o.getId(), 20_000_000);
        assertThat(player.getSalary()).isEqualTo(20_000_000);
    }

    @Test
    void 결정하지_않은_제안이_있으면_시리즈를_진행할_수_없다() {
        Player player = mlbPlayer(70);
        offer(player, Kind.EXTENSION, player.getTeam(), 4, 12_000_000, 12_000_000);
        assertThatThrownBy(() -> seasonService.playNextSeries(owner.getId(), player.getId()))
                .isInstanceOf(SeasonProgressException.class)
                .hasMessageContaining("계약");
    }

    @Test
    void 시즌_시작_전_FA_이적이면_새_시즌_소속도_바뀐다() {
        Player player = mlbPlayer(80);
        PlayerSeason season = seasonService.currentSeason(owner.getId(), player.getId());
        Team newTeam = teams.get(25);
        ContractOffer move = offerRepository.save(new ContractOffer(player.getId(), season.getSeasonYear(),
                Kind.FREE_AGENCY, newTeam, 2, 18_000_000, 18_000_000));

        contractService.accept(owner.getId(), player.getId(), move.getId());
        assertThat(season.getTeamId()).isEqualTo(newTeam.getId());
    }
}
