package com.mlbgame.season;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.trade.TradeEvent;
import com.mlbgame.trade.TradeEventRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * V19 마이그레이션의 기존 기록 복원(UPDATE) 이 시리즈마다 맞는 구단을 채우는지.
 * 마이그레이션 파일의 UPDATE 문을 그대로 읽어 실행한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("bugfix")
class SeriesTeamBackfillTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    PlayerSeasonRepository seasonRepository;

    @Autowired
    SeriesRecordRepository seriesRecordRepository;

    @Autowired
    TradeEventRepository tradeEventRepository;

    @Autowired
    TeamRepository teamRepository;

    @Test
    void 기존_시리즈_기록의_구단을_트레이드_이력으로_복원한다() throws Exception {
        List<Team> teams = teamRepository.findAllByOrderByIdAsc();
        Team from = teams.get(0);
        Team to = teams.get(1);
        Team ghost = teams.get(2);
        Team wrong = teams.get(29);       // 복원 전 아무 값 (복원으로 덮어써져야 한다)

        User owner = userRepository.save(new User("backfill", "{noop}pw", "복원"));
        Player player = Player.create(owner.getId(), "복원왕", 9, Position.SS, Hand.R, Hand.R, Nationality.KOREA,
                Education.COLLEGE, SchoolRegion.US_EAST, 60, Map.of(), List.of());
        player.completeCombine();
        player.draft(to, LeagueLevel.MLB);
        Long id = playerRepository.save(player).getId();

        // 2027: 3번째 시리즈 뒤 from → to 트레이드가 실제로 반영된 시즌 (시즌 소속 = to)
        seasonRepository.save(PlayerSeason.start(id, 2027, LeagueLevel.MLB, to.getId()));
        tradeEventRepository.save(new TradeEvent(id, 2027, 3, TradeEvent.Kind.TRADE, from.getId(), to.getId(), "트레이드"));
        // 2028: 트레이드 없음 (소속 to)
        seasonRepository.save(PlayerSeason.start(id, 2028, LeagueLevel.MLB, to.getId()));
        // 2029: 예전 버그 — 트레이드 기록(to → ghost)만 있고 이동은 저장되지 않음 (시즌 소속 to 그대로)
        seasonRepository.save(PlayerSeason.start(id, 2029, LeagueLevel.MLB, to.getId()));
        tradeEventRepository.save(new TradeEvent(id, 2029, 2, TradeEvent.Kind.TRADE, to.getId(), ghost.getId(), "트레이드"));

        for (int year : List.of(2027, 2028, 2029)) {
            for (int no = 1; no <= 5; no++) {
                seriesRecordRepository.save(new SeriesRecord(id, year, no, 0L, LeagueLevel.MLB, wrong.getId(),
                        "{}", 2, 1, "{}"));
            }
        }
        entityManager.flush();

        jdbc.update(backfillStatement());

        assertThat(teamsBySeries(id, 2027)).containsExactly(
                from.getId(), from.getId(), from.getId(), to.getId(), to.getId());
        assertThat(teamsBySeries(id, 2028)).containsOnly(to.getId());
        assertThat(teamsBySeries(id, 2029)).containsOnly(to.getId());   // 실제로 뛴 구단
    }

    private List<Long> teamsBySeries(Long playerId, int year) {
        return jdbc.queryForList("select team_id from series_records where player_id = ? and season_year = ? "
                + "order by series_no", Long.class, playerId, year);
    }

    /** V19 파일의 UPDATE 문 */
    private static String backfillStatement() throws Exception {
        String sql = new ClassPathResource("db/migration/V19__add_team_to_series_records.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int start = sql.indexOf("UPDATE series_records");
        return sql.substring(start, sql.indexOf(';', start));
    }
}
