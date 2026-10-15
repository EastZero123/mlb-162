package com.mlbgame.combine;

import com.mlbgame.common.Dice;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import com.mlbgame.league.TeamRepository;
import com.mlbgame.league.world.LeagueWorldService;
import com.mlbgame.league.world.WorldGenerator;
import com.mlbgame.season.earnings.EarningType;
import com.mlbgame.season.earnings.EarningsService;
import com.mlbgame.player.Education;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.PlayerStage;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 드래프트. 컴바인 등급·점수로 라운드를 정하고, 라운드 내 순번과 지명 팀은 랜덤.
 * 능력치는 바꾸지 않는다.
 */
@Service
public class DraftService {

    static final int TEAMS_PER_ROUND = 30;
    /** 드래프트 연도 (첫 시즌 2026 의 전년도) */
    public static final int DRAFT_YEAR = WorldGenerator.FIRST_SEASON - 1;
    /** 대졸은 이 라운드까지 싱글A에서 시작, 그 외(고졸 포함)는 루키리그 */
    static final int COLLEGE_A_LEVEL_MAX_ROUND = 10;

    private final PlayerService playerService;
    private final CombineSummaryRepository summaryRepository;
    private final DraftResultRepository draftResultRepository;
    private final TeamRepository teamRepository;
    private final LeagueWorldService leagueWorldService;
    private final EarningsService earningsService;
    private final Dice dice;

    public DraftService(PlayerService playerService, CombineSummaryRepository summaryRepository,
                        DraftResultRepository draftResultRepository, TeamRepository teamRepository,
                        LeagueWorldService leagueWorldService, EarningsService earningsService, Dice dice) {
        this.playerService = playerService;
        this.summaryRepository = summaryRepository;
        this.draftResultRepository = draftResultRepository;
        this.teamRepository = teamRepository;
        this.leagueWorldService = leagueWorldService;
        this.earningsService = earningsService;
        this.dice = dice;
    }

    @Transactional
    public DraftResult run(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        player.requireStage(PlayerStage.COMBINE_DONE); // 드래프트는 1회만
        CombineSummary summary = summaryRepository.findById(playerId).orElseThrow();

        int round = summary.getGrade().roundFor(summary.getTotalScore());
        int pickInRound = 1 + dice.nextInt(TEAMS_PER_ROUND);
        int overallPick = (round - 1) * TEAMS_PER_ROUND + pickInRound;

        List<Team> teams = teamRepository.findAllByOrderByIdAsc();
        Team team = teams.get(dice.nextInt(teams.size()));
        LeagueLevel startLevel = startLevel(player.getEducation(), round);

        player.draft(team, startLevel);
        DraftResult result = draftResultRepository.save(new DraftResult(player.getId(), round, pickInRound,
                overallPick, team, signingBonus(overallPick), startLevel));
        leagueWorldService.getOrCreate(player.getId());   // 이 커리어의 2026시즌 리그 월드 생성
        earningsService.add(player.getId(), DRAFT_YEAR, EarningType.SIGNING_BONUS, result.getSigningBonus());
        return result;
    }

    @Transactional(readOnly = true)
    public DraftResult find(Long userId, Long playerId) {
        playerService.getOwned(userId, playerId); // 소유권 확인
        return draftResultRepository.findById(playerId).orElse(null);
    }

    static LeagueLevel startLevel(Education education, int round) {
        return education == Education.COLLEGE && round <= COLLEGE_A_LEVEL_MAX_ROUND ? LeagueLevel.A : LeagueLevel.ROOKIE;
    }

    /** 전체 1순위 950만 달러에서 지명 순번마다 4%씩 감소, 최저 15만 달러 (1만 달러 단위) */
    static long signingBonus(int overallPick) {
        double bonus = 9_500_000 * Math.pow(0.96, overallPick - 1);
        return Math.max(150_000, Math.round(bonus / 10_000) * 10_000);
    }
}
