package com.mlbgame.player;

import com.mlbgame.common.Messages;
import com.mlbgame.config.GameBalance;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.mode.LeagueRulesets;
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

    /** 한 모드의 현역 선수 (대시보드) */
    @Transactional(readOnly = true)
    public List<Player> listActive(Long userId, LeagueMode mode) {
        return playerRepository.findByUserIdAndStatusAndLeagueModeOrderByIdAsc(userId, PlayerStatus.ACTIVE, mode);
    }

    @Transactional(readOnly = true)
    public List<Player> listRetired(Long userId, LeagueMode mode) {
        return playerRepository.findByUserIdAndStatusAndLeagueModeOrderByIdAsc(userId, PlayerStatus.RETIRED, mode);
    }

    @Transactional(readOnly = true)
    public boolean hasFreeSlot(Long userId) {
        return hasFreeSlot(userId, LeagueMode.MLB);
    }

    /** 슬롯은 모드별로 따로 센다 (MLB 3 + KBO 3) */
    @Transactional(readOnly = true)
    public boolean hasFreeSlot(Long userId, LeagueMode mode) {
        return playerRepository.countByUserIdAndStatusAndLeagueMode(userId, PlayerStatus.ACTIVE, mode)
                < balance.maxActivePlayers();
    }

    @Transactional
    public Player create(Long userId, PlayerCreateForm form) {
        return create(userId, form, LeagueMode.MLB);
    }

    @Transactional
    public Player create(Long userId, PlayerCreateForm form, LeagueMode mode) {
        if (!LeagueRulesets.isAvailable(mode)) {
            throw new IllegalArgumentException(Messages.get("mode.notReady", mode.getLabel()));   // 컨트롤러가 먼저 막는다
        }
        if (!hasFreeSlot(userId, mode)) {
            throw new SlotLimitExceededException(balance.maxActivePlayers(), mode);
        }
        Map<Attribute, Integer> allocation = form.allocationFor(form.getPosition());
        validateAllocation(allocation);

        Player player = Player.create(userId, form.getName().trim(), form.getUniformNumber(), form.getPosition(),
                form.getBats(), form.getThrowsHand(), form.getNationality(), form.getEducation(),
                form.getSchoolRegion(), startValue(form.getEducation()), allocation, form.pitchSelection(), mode);
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
                        Messages.get("create.error.allocationMax", entry.getKey().getLabel(), max));
            }
        }
        int total = allocation.values().stream().mapToInt(Integer::intValue).sum();
        if (total != balance.allocationPoints()) {
            throw new InvalidAllocationException(
                    Messages.get("create.error.allocationTotal", balance.allocationPoints(), total));
        }
    }

    /** 본인 소유 선수만 조회. 남의 선수는 존재 여부를 숨기기 위해 똑같이 404 */
    @Transactional(readOnly = true)
    public Player getOwned(Long userId, Long playerId) {
        return playerRepository.findByIdAndUserId(playerId, userId)
                .orElseThrow(PlayerNotFoundException::new);
    }
}
