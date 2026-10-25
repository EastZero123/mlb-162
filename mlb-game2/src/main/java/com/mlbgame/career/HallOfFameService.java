package com.mlbgame.career;

import com.mlbgame.award.Award;
import com.mlbgame.award.AwardService;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.player.Player;
import com.mlbgame.postseason.PostseasonRepository;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.PlayerSeasonRepository;
import com.mlbgame.season.SeriesRecord;
import com.mlbgame.season.SeriesRecordRepository;
import com.mlbgame.season.SeriesRecorder;
import com.mlbgame.season.SeriesSummary;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 현역 선수의 지금까지 명예의 전당 점수 (커리어 화면과 같은 식, {@link CareerService#hallOfFameScore}).
 * 시즌 진행 중에 쓰므로 {@code SeasonService} 에 의존하지 않는다 (CareerService 는 SeasonService 에 의존).
 */
@Service
public class HallOfFameService {

    private final PlayerSeasonRepository seasonRepository;
    private final SeriesRecordRepository seriesRecordRepository;
    private final SeriesRecorder seriesRecorder;
    private final AwardService awardService;
    private final PostseasonRepository postseasonRepository;

    public HallOfFameService(PlayerSeasonRepository seasonRepository, SeriesRecordRepository seriesRecordRepository,
                             SeriesRecorder seriesRecorder, AwardService awardService,
                             PostseasonRepository postseasonRepository) {
        this.seasonRepository = seasonRepository;
        this.seriesRecordRepository = seriesRecordRepository;
        this.seriesRecorder = seriesRecorder;
        this.awardService = awardService;
        this.postseasonRepository = postseasonRepository;
    }

    public int currentScore(Player player) {
        Long playerId = player.getId();
        BattingLine mlbBatting = new BattingLine();
        PitchingLine mlbPitching = new PitchingLine();
        for (SeriesRecord record : seriesRecordRepository.findByPlayerIdOrderBySeasonYearAscSeriesNoAsc(playerId)) {
            if (!record.getLeagueLevel().isTop()) {
                continue;
            }
            SeriesSummary summary = seriesRecorder.readSummary(record);
            if (summary.batting() != null) mlbBatting.add(summary.batting());
            if (summary.pitching() != null) mlbPitching.add(summary.pitching());
        }
        List<PlayerSeason> seasons = seasonRepository.findByPlayerIdOrderBySeasonYearAsc(playerId);
        int majorSeasons = (int) seasons.stream()
                .filter(s -> s.getMlbSeriesPlayed() >= CareerService.MAJOR_SEASON_SERIES).count();
        int awardPoints = awardService.career(playerId).stream().mapToInt(Award::getPoints).sum();
        Map<Integer, PlayerSeason> seasonByYear = seasons.stream()
                .collect(Collectors.toMap(PlayerSeason::getSeasonYear, Function.identity()));
        int championships = CareerService.championships(
                postseasonRepository.findByPlayerIdOrderBySeasonYearAsc(playerId), seasonByYear).size();
        return CareerService.hallOfFameScore(player.isPitcher(), mlbBatting, mlbPitching, awardPoints, majorSeasons,
                championships, LeagueRulesets.of(player).hallOfFameStatScale());
    }
}
