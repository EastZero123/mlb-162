package com.mlbgame.reputation;

import com.mlbgame.award.Award;
import com.mlbgame.award.AwardRepository;
import com.mlbgame.common.Messages;
import com.mlbgame.player.Player;
import com.mlbgame.season.PlayerSeason;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평판 변화 기록. 규칙: {@link ReputationRules} */
@Service
public class ReputationService {

    static final int MAJOR_SEASON_SERIES = 27;

    private final ReputationLogRepository repository;
    private final AwardRepository awardRepository;

    public ReputationService(ReputationLogRepository repository, AwardRepository awardRepository) {
        this.repository = repository;
        this.awardRepository = awardRepository;
    }

    /**
     * 평판을 바꾸고 이력을 남긴다 (호출하는 트랜잭션 안의 영속 Player 여야 저장된다).
     *
     * @return 실제로 바뀐 양 (0~100 범위 끝이면 0일 수 있다)
     */
    @Transactional
    public int change(Player player, int year, int delta, String reason) {
        if (delta == 0) {
            return 0;
        }
        int applied = player.changeReputation(delta);
        if (applied != 0) {
            repository.save(new ReputationLog(player.getId(), year, applied, player.getReputation(), reason));
        }
        return applied;
    }

    @Transactional(readOnly = true)
    public List<ReputationLog> recent(Long playerId) {
        return repository.findTop5ByPlayerIdOrderByIdDesc(playerId);
    }

    /**
     * 시즌 결산 (시즌 전환 때): 수상 포인트당 +1(최대 +5), 월드시리즈 우승 +5, 메이저 주요 시즌 +1.
     *
     * @return 시즌 전환 소식 문장 (변화가 없으면 빈 목록)
     */
    @Transactional
    public List<String> seasonReview(Player player, PlayerSeason season, boolean champion) {
        int year = season.getSeasonYear();
        int awardPoints = awardRepository.findByPlayerIdAndSeasonYearOrderByIdAsc(player.getId(), year).stream()
                .mapToInt(Award::getPoints).sum();
        List<String> parts = new ArrayList<>();
        int total = 0;
        int award = change(player, year, ReputationRules.awardBonus(awardPoints), Messages.get("reputation.reason.awards", year));
        if (award != 0) {
            parts.add(Messages.get("reputation.part.awards", award));
            total += award;
        }
        if (champion) {
            int ring = change(player, year, ReputationRules.CHAMPIONSHIP, Messages.get("reputation.reason.championship", year));
            if (ring != 0) {
                parts.add(Messages.get("reputation.part.championship", ring));
                total += ring;
            }
        }
        if (season.getMlbSeriesPlayed() >= MAJOR_SEASON_SERIES) {
            int major = change(player, year, ReputationRules.MAJOR_SEASON, Messages.get("reputation.reason.majorSeason", year));
            if (major != 0) {
                parts.add(Messages.get("reputation.part.majorSeason", major));
                total += major;
            }
        }
        if (total == 0) {
            return List.of();
        }
        return List.of(Messages.get("reputation.news", String.join(", ", parts), player.getReputation(),
                ReputationRules.grade(player.getReputation())));
    }
}
