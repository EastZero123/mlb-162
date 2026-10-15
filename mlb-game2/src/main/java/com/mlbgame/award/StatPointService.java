package com.mlbgame.award;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.training.InvalidTrainingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스탯업 포인트 사용: 보유 포인트 1개 = 원하는 능력치 +1. 훈련과 달리 언제든 쓸 수 있고, 시즌을 넘어 이월된다.
 */
@Service
public class StatPointService {

    private final PlayerService playerService;
    private final StatPointLogRepository logRepository;

    public StatPointService(PlayerService playerService, StatPointLogRepository logRepository) {
        this.playerService = playerService;
        this.logRepository = logRepository;
    }

    @Transactional
    public StatPointLog spend(Long userId, Long playerId, Attribute attribute) {
        Player player = playerService.getOwned(userId, playerId);
        if (player.getStatPoints() <= 0) {
            throw new InvalidTrainingException("보유한 스탯업 포인트가 없습니다.");
        }
        if (!player.canTrain(attribute)) {
            throw new InvalidTrainingException(attribute.getLabel() + "에는 포인트를 쓸 수 없습니다. (포지션에 없거나 이미 100)");
        }
        int valueAfter = player.spendStatPoint(attribute);
        return logRepository.save(new StatPointLog(player.getId(), attribute, valueAfter));
    }
}
