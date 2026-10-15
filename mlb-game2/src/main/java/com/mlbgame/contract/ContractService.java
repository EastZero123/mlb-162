package com.mlbgame.contract;

import com.mlbgame.award.Award;
import com.mlbgame.award.AwardRepository;
import com.mlbgame.common.Dice;
import com.mlbgame.contract.ContractOffer.Kind;
import com.mlbgame.contract.ContractOffer.Status;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.world.LeagueWorld;
import com.mlbgame.league.world.NpcPlayer;
import com.mlbgame.league.world.NpcPlayerRepository;
import com.mlbgame.league.world.NpcStatus;
import com.mlbgame.league.world.Payroll;
import com.mlbgame.league.world.WorldTeam;
import com.mlbgame.league.world.WorldTeamRepository;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.PlayerSeasonRepository;
import com.mlbgame.season.SeasonProgressException;
import com.mlbgame.training.InvalidTrainingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 선수 계약·연봉. 설계: docs/league-world.md 8장
 *
 * <ul>
 *   <li>시즌 중: 메이저에 올라오면 최저 연봉 계약, 시리즈마다 연봉 1/54 적립 (보장 계약은 마이너에서도 계약 연봉)</li>
 *   <li>오프시즌(시즌 전환): 다년 계약은 1년 경과 / 만료 시 서비스 연수에 따라 최저 연봉 재계약·연봉조정·FA, 연장 계약 제안(확률)</li>
 *   <li>협상: 수락, 연봉조정 청문회, FA 역제안·거절. 결정하지 않은 제안이 있으면 새 시즌 시리즈를 진행할 수 없다</li>
 * </ul>
 */
@Service
public class ContractService {

    static final int MAX_FA_OFFERS = 4;
    static final double FALLBACK_RATE = 0.6;

    private final PlayerService playerService;
    private final PlayerSeasonRepository seasonRepository;
    private final ContractOfferRepository offerRepository;
    private final AwardRepository awardRepository;
    private final WorldTeamRepository worldTeamRepository;
    private final NpcPlayerRepository npcPlayerRepository;
    private final Dice dice;

    public ContractService(PlayerService playerService, PlayerSeasonRepository seasonRepository,
                           ContractOfferRepository offerRepository, AwardRepository awardRepository,
                           WorldTeamRepository worldTeamRepository, NpcPlayerRepository npcPlayerRepository, Dice dice) {
        this.playerService = playerService;
        this.seasonRepository = seasonRepository;
        this.offerRepository = offerRepository;
        this.awardRepository = awardRepository;
        this.worldTeamRepository = worldTeamRepository;
        this.npcPlayerRepository = npcPlayerRepository;
        this.dice = dice;
    }

    // ------------------------------------------------------------ 시즌 중

    @Transactional(readOnly = true)
    public int serviceYears(Long playerId) {
        int series = seasonRepository.findByPlayerIdOrderBySeasonYearAsc(playerId).stream()
                .mapToInt(PlayerSeason::getMlbSeriesPlayed).sum();
        return ContractRules.serviceYears(series);
    }

    /** 메이저에 처음 올라오면 최저 연봉 계약 */
    public void ensureMlbContract(Player player, LeagueLevel level) {
        if (level == LeagueLevel.MLB && player.getContractType() == ContractType.NONE) {
            player.signContract(null, Payroll.MINIMUM_SALARY, 1, ContractType.PRE_ARB);
        }
    }

    /** 시리즈 1회분 연봉 */
    public static long seriesSalary(Player player, LeagueLevel level) {
        long annual;
        if (player.getContractType().isGuaranteed()) {
            annual = player.getSalary();
        } else if (level == LeagueLevel.MLB) {
            annual = Math.max(player.getSalary(), Payroll.MINIMUM_SALARY);
        } else {
            annual = Payroll.minorLeagueSalary(level);
        }
        return Math.round((double) annual / PlayerSeason.SERIES_PER_SEASON);
    }

    @Transactional(readOnly = true)
    public boolean hasPending(Long playerId) {
        return offerRepository.existsByPlayerIdAndStatus(playerId, Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ContractOffer> pending(Long playerId) {
        return offerRepository.findByPlayerIdAndStatusOrderByIdAsc(playerId, Status.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ContractOffer> history(Long playerId) {
        return offerRepository.findByPlayerIdOrderByIdDesc(playerId).stream().filter(o -> !o.isPending()).toList();
    }

    // ------------------------------------------------------------ 오프시즌

    /**
     * 시즌 전환 때 호출. 다년 계약 경과, 만료 시 재계약·연봉조정·FA 제안 생성, 연장 계약 제안(확률).
     *
     * @param closingYear 방금 끝난 시즌
     * @return 오프시즌 소식 문장
     */
    @Transactional
    public List<String> offseason(Player player, LeagueWorld world, int closingYear) {
        List<String> lines = new ArrayList<>();
        if (player.getContractType() == ContractType.NONE) {
            return lines;                                            // 마이너 계약 — 레벨별 연봉
        }
        int newYear = closingYear + 1;
        int service = serviceYears(player.getId());
        int awardPoints = awardRepository.findByPlayerIdAndSeasonYearOrderByIdAsc(player.getId(), closingYear)
                .stream().mapToInt(Award::getPoints).sum();
        double value = ContractRules.valueOverall(player.getOverall(), awardPoints);

        if (player.getContractYears() > 1) {
            player.passContractYear();
            lines.add("계약: " + player.getContractType().getLabel() + " " + player.getContractYears() + "년 남음 ("
                    + money(player.getSalary()) + "/년)");
            return lines;
        }

        if (service < Payroll.ARBITRATION_START) {
            player.signContract(null, Payroll.MINIMUM_SALARY, 1, ContractType.PRE_ARB);
            lines.add("계약: 서비스 " + service + "년 — 최저 연봉 " + money(Payroll.MINIMUM_SALARY) + "으로 재계약");
        } else if (service < Payroll.FREE_AGENCY_SERVICE) {
            long fair = ContractRules.fairArbitration(service, value);
            long offer = ContractRules.arbitrationOffer(fair);
            offerRepository.save(new ContractOffer(player.getId(), newYear, Kind.ARBITRATION, player.getTeam(), 1, offer, fair));
            lines.add("계약: 연봉조정 대상 (서비스 " + service + "년) — 구단 제시 " + money(offer) + ". 계약 화면에서 협상하세요");
        } else {
            int count = createFreeAgentOffers(player, world, newYear, value);
            lines.add("계약: FA 자격 취득! " + count + "개 구단이 오퍼를 보냈습니다. 계약 화면에서 협상하세요");
        }

        if (service >= 2 && service < Payroll.FREE_AGENCY_SERVICE && value >= ContractRules.EXTENSION_MIN_OVR
                && dice.nextDouble() < ContractRules.EXTENSION_CHANCE) {
            int years = ContractRules.extensionYears(service);
            long salary = ContractRules.extensionSalary(value);
            offerRepository.save(new ContractOffer(player.getId(), newYear, Kind.EXTENSION, player.getTeam(), years,
                    salary, salary));
            lines.add("계약: " + player.getTeam().getNameKo() + "가 " + years + "년 " + money(salary) + "/년 연장 계약을 제안했습니다");
        }
        return lines;
    }

    private int createFreeAgentOffers(Player player, LeagueWorld world, int year, double value) {
        Map<Long, Long> payroll = new HashMap<>();
        for (NpcPlayer npc : npcPlayerRepository.findByWorldIdAndStatus(world.getId(), NpcStatus.MLB)) {
            payroll.merge(npc.getTeamId(), npc.getSalary(), Long::sum);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (WorldTeam team : worldTeamRepository.findByWorldIdOrderByIdAsc(world.getId())) {
            long salary = ContractRules.freeAgentSalary(value, team.getMarket(), dice.nextDouble());
            long room = team.getBudget() - payroll.getOrDefault(team.getTeam().getId(), 0L);
            boolean current = team.getTeam().getId().equals(player.getTeam().getId());
            if (room >= salary || (current && room >= salary * 0.8)) {
                candidates.add(new Candidate(team, salary, room * (0.6 + 0.8 * dice.nextDouble()) + (current ? 1e12 : 0)));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        int count = 0;
        for (Candidate c : candidates.stream().limit(MAX_FA_OFFERS).toList()) {
            int years = ContractRules.freeAgentYears(player.getAge(), dice.nextDouble());
            offerRepository.save(new ContractOffer(player.getId(), year, Kind.FREE_AGENCY, c.team().getTeam(), years,
                    c.salary(), Payroll.marketValue(value)));
            count++;
        }
        if (count == 0) {
            fallbackOffer(player, year, value);
            count = 1;
        }
        return count;
    }

    private record Candidate(WorldTeam team, long salary, double score) {
    }

    /** 협상이 모두 결렬되면 원소속팀이 1년 저가 계약을 제안 (FA 미아 방지) */
    private void fallbackOffer(Player player, int year, double value) {
        long salary = Math.max(Payroll.MINIMUM_SALARY, ContractRules.round(Payroll.marketValue(value) * FALLBACK_RATE));
        offerRepository.save(new ContractOffer(player.getId(), year, Kind.FREE_AGENCY, player.getTeam(), 1, salary, salary));
    }

    // ------------------------------------------------------------ 협상

    @Transactional
    public ContractOffer accept(Long userId, Long playerId, Long offerId) {
        Player player = playerService.getOwned(userId, playerId);
        ContractOffer offer = pendingOffer(playerId, offerId);
        sign(player, offer, offer.getSalary(), "수락");
        return offer;
    }

    /** 연봉조정 청문회: 내 요구액 vs 구단 제시액, 적정 연봉에 가까운 쪽으로 결정 */
    @Transactional
    public ContractOffer arbitrate(Long userId, Long playerId, Long offerId, long ask) {
        Player player = playerService.getOwned(userId, playerId);
        ContractOffer offer = pendingOffer(playerId, offerId);
        if (offer.getKind() != Kind.ARBITRATION) {
            throw new InvalidTrainingException("연봉조정 제안에서만 청문회를 신청할 수 있습니다.");
        }
        if (ask < offer.getSalary()) {
            throw new InvalidTrainingException("요구액은 구단 제시액 이상이어야 합니다.");
        }
        long result = ContractRules.hearing(ask, offer.getSalary(), offer.getFairValue());
        sign(player, offer, result, result == ask ? "청문회 승리 — 요구액 " + money(ask) + " 채택" : "청문회 패배 — 구단 제시액 채택");
        return offer;
    }

    /** FA 역제안: 요구액이 오퍼보다 높을수록 구단이 협상을 접을 확률이 커진다 */
    @Transactional
    public ContractOffer counter(Long userId, Long playerId, Long offerId, long ask) {
        Player player = playerService.getOwned(userId, playerId);
        ContractOffer offer = pendingOffer(playerId, offerId);
        if (offer.getKind() != Kind.FREE_AGENCY) {
            throw new InvalidTrainingException("FA 오퍼에서만 역제안할 수 있습니다.");
        }
        if (dice.nextDouble() < ContractRules.counterAcceptance(ask, offer.getSalary())) {
            sign(player, offer, Math.max(ask, offer.getSalary()), "역제안 수락 — " + money(Math.max(ask, offer.getSalary())));
        } else {
            offer.reject("역제안 거절 — " + offer.getTeam().getNameKo() + "가 협상에서 철수했습니다");
            ensureFreeAgentFallback(player, offer);
        }
        return offer;
    }

    /** 연장 제안 거절 / FA 오퍼 거절 (연봉조정은 거절 불가) */
    @Transactional
    public ContractOffer decline(Long userId, Long playerId, Long offerId) {
        Player player = playerService.getOwned(userId, playerId);
        ContractOffer offer = pendingOffer(playerId, offerId);
        if (offer.getKind() == Kind.ARBITRATION) {
            throw new InvalidTrainingException("연봉조정은 수락하거나 청문회로 결정해야 합니다.");
        }
        offer.reject("거절");
        if (offer.getKind() == Kind.FREE_AGENCY) {
            ensureFreeAgentFallback(player, offer);
        }
        return offer;
    }

    private void ensureFreeAgentFallback(Player player, ContractOffer lost) {
        boolean anyLeft = pending(player.getId()).stream().anyMatch(o -> o.getKind() == Kind.FREE_AGENCY && o != lost);
        if (!anyLeft) {
            fallbackOffer(player, lost.getSeasonYear(), player.getOverall());
        }
    }

    private void sign(Player player, ContractOffer offer, long salary, String note) {
        ContractType type = switch (offer.getKind()) {
            case ARBITRATION -> ContractType.ARBITRATION;
            case EXTENSION -> ContractType.EXTENSION;
            case FREE_AGENCY -> ContractType.FREE_AGENT;
        };
        boolean moving = !offer.getTeam().getId().equals(player.getTeam().getId());
        player.signContract(offer.getTeam(), salary, offer.getYears(), type);
        offer.accept(salary, note);
        // 같은 오프시즌의 다른 제안은 정리 (연장 수락 → 연봉조정 소멸, FA 계약 → 다른 FA 오퍼 소멸)
        for (ContractOffer other : pending(player.getId())) {
            if (other != offer) {
                other.reject("다른 계약 체결로 소멸");
            }
        }
        if (moving) {
            seasonRepository.findByPlayerIdAndSeasonYear(player.getId(), offer.getSeasonYear())
                    .ifPresent(season -> season.relocate(offer.getTeam().getId()));
        }
    }

    private ContractOffer pendingOffer(Long playerId, Long offerId) {
        ContractOffer offer = offerRepository.findById(offerId)
                .filter(o -> o.getPlayerId().equals(playerId))
                .orElseThrow(() -> new SeasonProgressException("계약 제안을 찾을 수 없습니다."));
        if (!offer.isPending()) {
            throw new SeasonProgressException("이미 결정된 제안입니다.");
        }
        return offer;
    }

    static String money(long value) {
        return "$" + NumberFormat.getIntegerInstance(Locale.US).format(value);
    }
}
