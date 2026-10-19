package com.mlbgame.award;

import com.mlbgame.common.Messages;
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
            throw new InvalidTrainingException(Messages.get("error.noStatPoints"));
        }
        if (!player.canTrain(attribute)) {
            throw new InvalidTrainingException(Messages.get("points.error.cannotSpend", attribute.getLabel()));
        }
        int valueAfter = player.spendStatPoint(attribute);
        return logRepository.save(new StatPointLog(player.getId(), attribute, valueAfter));
    }
}
