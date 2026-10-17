package com.mlbgame.league.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 시즌별 리그 평균: 저장값 → 진행 중 시즌 즉석 계산 → 기준값 순서 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class LeagueAverageServiceTest {

    @Autowired
    LeagueAverageService service;

    @Autowired
    LeagueSeasonAverageRepository repository;

    @Autowired
    LeagueWorldService leagueWorldService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamRepository teamRepository;

    LeagueWorld world;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(new User("lgavg", "{noop}pw", "리그평균"));
        Player player = Player.create(owner.getId(), "평균왕", 1, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 60, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(0), LeagueLevel.MLB);
        world = leagueWorldService.getOrCreate(playerRepository.save(player).getId());
    }

    @Test
    void 시즌_전체_리그_평균을_한_번만_저장한다() {
        int year = world.getCurrentYear();

        LeagueAverages first = service.snapshot(world, year);
        LeagueAverages second = service.snapshot(world, year);

        assertThat(first.usable()).isTrue();
        assertThat(second).isEqualTo(first);
        assertThat(repository.findByWorldIdAndSeasonYear(world.getId(), year)).isPresent();
        assertThat(service.forYear(world, year, 0)).isEqualTo(first);           // 저장값이 우선
    }

    @Test
    void 저장된_리그_평균은_현실적인_범위다() {
        LeagueAverages lg = service.snapshot(world, world.getCurrentYear());

        assertThat(lg.obp()).isBetween(0.28, 0.36);
        assertThat(lg.woba()).isBetween(0.27, 0.36);
        assertThat(lg.era()).isBetween(3.0, 6.0);
        assertThat(lg.runsPerPa()).isBetween(0.08, 0.16);
    }

    @Test
    void 진행_중인_시즌은_끝난_달까지의_기록으로_계산한다() {
        int year = world.getCurrentYear();

        assertThat(service.forYear(world, year, 0)).isEqualTo(LeagueAverages.BASELINE);   // 아직 한 달도 안 지남
        LeagueAverages live = service.forYear(world, year, 9);                            // 4월 끝
        assertThat(live).isNotEqualTo(LeagueAverages.BASELINE);
        assertThat(live.usable()).isTrue();
        assertThat(repository.findByWorldIdAndSeasonYear(world.getId(), year)).isEmpty(); // 즉석 계산은 저장 안 함
    }

    @Test
    void 저장값이_없는_지난_시즌과_월드가_없는_경우는_기준값() {
        assertThat(service.forYear(world, world.getCurrentYear() - 1, 54)).isEqualTo(LeagueAverages.BASELINE);
        assertThat(service.forYear(null, 2026, 54)).isEqualTo(LeagueAverages.BASELINE);
    }
}
