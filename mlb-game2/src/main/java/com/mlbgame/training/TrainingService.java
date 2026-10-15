package com.mlbgame.training;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.season.SeasonService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 훈련: 시리즈가 끝날 때마다 1회, 능력치 하나를 확정 +1. 훈련해야 다음 시리즈를 진행할 수 있다.
 *
 * <p>모든 훈련은 {@link #apply} 한 곳을 지난다 — 지금은 유저가 고르지만(MANUAL),
 * 자동 진행 모드를 넣게 되면 {@link #trainAutomatically} 처럼 고르는 쪽만 바꾸면 된다.
 */
@Service
public class TrainingService {

    private final PlayerService playerService;
    private final SeasonService seasonService;
    private final TrainingLogRepository logRepository;

    public TrainingService(PlayerService playerService, SeasonService seasonService,
                           TrainingLogRepository logRepository) {
        this.playerService = playerService;
        this.seasonService = seasonService;
        this.logRepository = logRepository;
    }

    @Transactional
    public TrainingLog train(Long userId, Long playerId, Attribute attribute) {
        return apply(userId, playerId, attribute, TrainingSource.MANUAL);
    }

    /** 자동 훈련 순서로 고르기 (자동 진행 모드 도입 대비, 화면에는 아직 연결하지 않음) */
    @Transactional
    public TrainingLog trainAutomatically(Long userId, Long playerId, List<Attribute> order) {
        Player player = playerService.getOwned(userId, playerId);
        PlayerSeason season = seasonService.currentSeason(userId, playerId);
        long trained = logRepository.countByPlayerIdAndSeasonYear(playerId, season.getSeasonYear());
        return apply(userId, playerId, AutoTrainingOrder.choose(player, order, trained), TrainingSource.AUTO);
    }

    @Transactional(readOnly = true)
    public List<TrainingLog> history(Long playerId, int seasonYear) {
        return logRepository.findByPlayerIdAndSeasonYearOrderBySeriesNoAsc(playerId, seasonYear);
    }

    private TrainingLog apply(Long userId, Long playerId, Attribute attribute, TrainingSource source) {
        Player player = playerService.getOwned(userId, playerId);
        PlayerSeason season = seasonService.currentSeason(userId, playerId);
        if (!season.isTrainingPending()) {
            throw new SeasonProgressException("지금은 훈련할 차례가 아닙니다. 다음 시리즈를 먼저 진행해 주세요.");
        }
        if (!player.canTrain(attribute)) {
            throw new InvalidTrainingException(attribute.getLabel() + "은(는) 훈련할 수 없습니다. (포지션에 없거나 이미 100)");
        }
        int valueAfter = player.train(attribute);
        season.completeTraining();
        return logRepository.save(new TrainingLog(player.getId(), season.getSeasonYear(), season.getSeriesPlayed(),
                attribute, valueAfter, source));
    }
}
