package com.mlbgame.player;

import com.mlbgame.config.GameBalance;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final GameBalance balance;

    public PlayerService(PlayerRepository playerRepository, GameBalance balance) {
        this.playerRepository = playerRepository;
        this.balance = balance;
    }

    @Transactional(readOnly = true)
    public List<Player> listActive(Long userId) {
        return playerRepository.findByUserIdAndStatusOrderByIdAsc(userId, PlayerStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Player> listRetired(Long userId) {
        return playerRepository.findByUserIdAndStatusOrderByIdAsc(userId, PlayerStatus.RETIRED);
    }

    @Transactional(readOnly = true)
    public boolean hasFreeSlot(Long userId) {
        return playerRepository.countByUserIdAndStatus(userId, PlayerStatus.ACTIVE) < balance.maxActivePlayers();
    }

    @Transactional
    public Player create(Long userId, PlayerCreateForm form) {
        if (!hasFreeSlot(userId)) {
            throw new SlotLimitExceededException(balance.maxActivePlayers());
        }
        Map<Attribute, Integer> allocation = form.allocationFor(form.getPosition());
        validateAllocation(allocation);

        Player player = Player.create(userId, form.getName().trim(), form.getUniformNumber(), form.getPosition(),
                form.getBats(), form.getThrowsHand(), form.getNationality(), form.getEducation(),
                form.getSchoolRegion(), startValue(form.getEducation()), allocation, form.pitchSelection());
        return playerRepository.save(player);
    }

    public int startValue(Education education) {
        return education == Education.HIGH_SCHOOL ? balance.highSchoolStart() : balance.collegeStart();
    }

    /** 배분 포인트는 전부 사용해야 하고, 능력치 하나에 0 ~ 한도까지만 넣을 수 있다 */
    void validateAllocation(Map<Attribute, Integer> allocation) {
        int max = balance.allocationMaxPerAttribute();
        for (Map.Entry<Attribute, Integer> entry : allocation.entrySet()) {
            int points = entry.getValue();
            if (points < 0 || points > max) {
                throw new InvalidAllocationException(
                        entry.getKey().getLabel() + "에는 0~" + max + "포인트까지 배분할 수 있습니다.");
            }
        }
        int total = allocation.values().stream().mapToInt(Integer::intValue).sum();
        if (total != balance.allocationPoints()) {
            throw new InvalidAllocationException(
                    "배분 포인트 " + balance.allocationPoints() + "을 모두 사용해 주세요. (현재 " + total + ")");
        }
    }

    /** 본인 소유 선수만 조회. 남의 선수는 존재 여부를 숨기기 위해 똑같이 404 */
    @Transactional(readOnly = true)
    public Player getOwned(Long userId, Long playerId) {
        return playerRepository.findByIdAndUserId(playerId, userId)
                .orElseThrow(PlayerNotFoundException::new);
    }
}
