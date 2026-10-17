package com.mlbgame.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.common.Dice;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.season.PlayerSeasonRepository;
import com.mlbgame.season.SeasonService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 트레이드 요청이 성사되면 소속 구단 변경이 DB 에 저장되는지 (회귀 테스트).
 * 테스트 트랜잭션이 엔티티를 붙잡고 있으면 버그가 가려지므로 일부러 @Transactional 을 붙이지 않는다
 * (대신 끝나면 만든 데이터를 직접 지운다 — 다른 테스트와 H2 DB 를 같이 쓰므로).
 * 랜덤은 0으로 고정 → 트레이드 요청은 항상 성사된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("bugfix")
class TradeRequestPersistenceTest {

    @TestConfiguration
    static class FixedDiceConfig {
        @Bean
        @Primary
        Dice fixedDice() {
            return new Dice() {
                @Override
                public int nextInt(int bound) {
                    return 0;
                }

                @Override
                public double nextDouble() {
                    return 0;
                }

                @Override
                public double nextGaussian() {
                    return 0;
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SeasonService seasonService;

    @Autowired
    TradeService tradeService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    PlayerSeasonRepository seasonRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    JdbcTemplate jdbc;

    Long userId;
    Long playerId;

    @AfterEach
    void cleanUp() {
        if (playerId != null) {
            for (Long world : jdbc.queryForList("select id from league_worlds where player_id = ?", Long.class, playerId)) {
                jdbc.update("delete from npc_players where world_id = ?", world);
                jdbc.update("delete from world_teams where world_id = ?", world);
                jdbc.update("delete from league_season_averages where world_id = ?", world);
            }
            jdbc.queryForList("select table_name from information_schema.columns where column_name = 'player_id'"
                            + " and table_schema = 'public' and table_name <> 'league_worlds'", String.class)
                    .forEach(table -> jdbc.update("delete from " + table + " where player_id = ?", playerId));
            jdbc.update("delete from league_worlds where player_id = ?", playerId);
            jdbc.update("delete from players where id = ?", playerId);
        }
        if (userId != null) {
            jdbc.update("delete from users where id = ?", userId);
        }
    }

    @Test
    void 트레이드_요청이_성사되면_새_구단이_저장된다() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(new User("trade-" + unique, "{noop}pw", "tr-" + unique));
        userId = owner.getId();
        Player player = Player.create(owner.getId(), "윤이적", 17, Position.B3, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 70, Map.of(), List.of());
        player.completeCombine();
        player.draft(teamRepository.findAllByOrderByIdAsc().get(12), LeagueLevel.MLB);
        player = playerRepository.save(player);
        playerId = player.getId();
        Long originalTeam = player.getTeam().getId();
        seasonService.currentSeason(owner.getId(), player.getId());

        mockMvc.perform(post("/players/{id}/season/trade-request", player.getId()).with(csrf())
                        .with(user(new AuthUser(owner))))
                .andExpect(redirectedUrl("/players/" + player.getId() + "/season"));

        List<TradeEvent> events = tradeService.career(player.getId());
        assertThat(events).extracting(TradeEvent::getKind).containsExactly(TradeEvent.Kind.TRADE);
        Long newTeam = events.get(0).getToTeamId();
        assertThat(newTeam).isNotEqualTo(originalTeam);

        Player reloaded = playerRepository.findById(player.getId()).orElseThrow();
        assertThat(reloaded.getTeam().getId()).isEqualTo(newTeam);
        assertThat(seasonRepository.findByPlayerIdOrderBySeasonYearAsc(player.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.getTeamId()).isEqualTo(newTeam));
    }
}
