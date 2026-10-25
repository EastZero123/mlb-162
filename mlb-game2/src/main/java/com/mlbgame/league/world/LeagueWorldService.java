package com.mlbgame.league.world;

import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.league.world.WorldGenerator.GeneratedNpc;
import com.mlbgame.league.world.WorldGenerator.GeneratedTeam;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 월드 생성·조회. 드래프트가 끝나면 그 커리어의 2026시즌 월드를 만든다.
 */
@Service
public class LeagueWorldService {

    private static final String INSERT_NPC = """
            INSERT INTO npc_players (world_id, team_id, name, position, bats_hand, throws_hand, age, potential,
                attribute_values, roster_role, status, salary, contract_years, service_years)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final LeagueWorldRepository worldRepository;
    private final WorldTeamRepository worldTeamRepository;
    private final TeamRepository teamRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlayerRepository playerRepository;

    public LeagueWorldService(LeagueWorldRepository worldRepository, WorldTeamRepository worldTeamRepository,
                              TeamRepository teamRepository, JdbcTemplate jdbcTemplate,
                              PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        this.worldRepository = worldRepository;
        this.worldTeamRepository = worldTeamRepository;
        this.teamRepository = teamRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 이미 있으면 그대로, 없으면 새 시드로 생성 */
    @Transactional
    public LeagueWorld getOrCreate(Long playerId) {
        return worldRepository.findByPlayerId(playerId).orElseGet(() -> create(playerId, ThreadLocalRandom.current().nextLong()));
    }

    @Transactional
    public LeagueWorld create(Long playerId, long seed) {
        // 월드는 선수의 모드를 따른다 (그 모드의 구단·연봉 규칙)
        LeagueMode mode = playerRepository.findById(playerId).map(Player::getLeagueMode).orElse(LeagueMode.MLB);
        LeagueWorld world = worldRepository.save(new LeagueWorld(playerId, seed, WorldGenerator.FIRST_SEASON, mode));
        List<GeneratedTeam> generated = WorldGenerator.generate(seed, teamRepository.findByLeagueModeOrderByIdAsc(mode),
                LeagueRulesets.of(mode).payroll());

        List<Object[]> npcRows = new ArrayList<>();
        for (GeneratedTeam team : generated) {
            worldTeamRepository.save(new WorldTeam(world.getId(), team.team(), team.market(),
                    team.spendingPercent(), team.farmScore(), team.budget()));
            for (GeneratedNpc npc : team.roster()) {
                npcRows.add(new Object[]{world.getId(), team.team().getId(), npc.name(), npc.position().name(),
                        npc.bats().name(), npc.throwsHand().name(), npc.age(), npc.potential(),
                        NpcPlayer.encode(npc.attributes()), npc.role().name(), NpcStatus.MLB.name(),
                        npc.salary(), npc.contractYears(), npc.serviceYears()});
            }
        }
        // 약 780행 — 한 번에 배치 삽입 (MySQL 은 URL 에 rewriteBatchedStatements=true 를 주면 더 빠름)
        jdbcTemplate.batchUpdate(INSERT_NPC, npcRows);
        return world;
    }

    @Transactional(readOnly = true)
    public List<WorldTeam> teams(Long worldId) {
        return worldTeamRepository.findByWorldIdOrderByIdAsc(worldId);
    }
}
