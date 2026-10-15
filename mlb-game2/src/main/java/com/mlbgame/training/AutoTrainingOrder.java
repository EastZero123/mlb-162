package com.mlbgame.training;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Player;
import java.util.List;

/**
 * 자동 훈련 순서 (자동 진행 모드용 — 도입 여부 미정, 구조만 준비).
 * 순서 목록을 돌아가며 고르고, 100에 도달했거나 포지션에 없는 능력치는 건너뛴다.
 */
public final class AutoTrainingOrder {

    private AutoTrainingOrder() {
    }

    /**
     * @param trainedSoFar 이번 시즌 지금까지 훈련한 횟수 — 순서의 시작 위치
     */
    public static Attribute choose(Player player, List<Attribute> order, long trainedSoFar) {
        if (order.isEmpty()) {
            throw new InvalidTrainingException("자동 훈련 순서가 비어 있습니다.");
        }
        int start = (int) (trainedSoFar % order.size());
        for (int i = 0; i < order.size(); i++) {
            Attribute candidate = order.get((start + i) % order.size());
            if (player.canTrain(candidate)) {
                return candidate;
            }
        }
        throw new InvalidTrainingException("자동 훈련 순서의 능력치가 모두 최대치이거나 포지션에 없습니다.");
    }
}
