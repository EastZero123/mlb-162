package com.mlbgame.career;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.award.Award;
import com.mlbgame.award.AwardType;
import com.mlbgame.award.StatPointService;
import com.mlbgame.combine.CombineService;
import com.mlbgame.combine.DraftResult;
import com.mlbgame.combine.DraftService;
import com.mlbgame.combine.InterviewAnswer;
import com.mlbgame.common.StatFormat;
import com.mlbgame.contract.ContractOffer;
import com.mlbgame.contract.ContractService;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.league.mode.LeagueRulesets;
import com.mlbgame.player.Attribute;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.PitchType;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerCreateForm;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import com.mlbgame.postseason.Postseason;
import com.mlbgame.postseason.PostseasonService;
import com.mlbgame.season.PlayerSeason;
import com.mlbgame.season.SeasonService;
import com.mlbgame.season.transition.SeasonTransitionService;
import com.mlbgame.simulation.rules.LineupRules;
import com.mlbgame.simulation.game.BattingLine;
import com.mlbgame.simulation.game.PitchingLine;
import com.mlbgame.training.AutoTrainingOrder;
import com.mlbgame.training.TrainingService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 장기 커리어 밸런스 검증 (M5-3): 실제 서비스 흐름 그대로 선수 생성 → 컴바인 → 드래프트 → 시즌(자동 훈련) →
 * 포스트시즌 → 오프시즌(계약 자동 결정) → 은퇴까지 진행하고, 데뷔 시기·수상 빈도·연봉 규모·은퇴 나이를 리포트한다.
 *
 * <p>아주 오래 걸리므로 {@code gradlew careerTest -Pcareers=3} 으로 따로 돌린다 (프로필당 커리어 수, 기본 2).
 * 테스트 트랜잭션 없이 서비스 트랜잭션 단위로 커밋하므로 인메모리 H2 에 데이터가 쌓인다.
 *
 * <p>자동 진행 정책
 * <ul>
 *   <li>훈련·스탯업 포인트: 포지션 능력치 11개를 순서대로 돌아가며 (균형형)</li>
 *   <li>계약: 연장 제안이 있으면 수락, 아니면 총액(연봉×연수)이 가장 큰 제안 수락. 청문회·역제안은 쓰지 않음</li>
 *   <li>은퇴: {@value #VOLUNTARY_AGE}세 이상이고 그 시즌 메이저 출전이 없거나 메이저에서 벤치 신세면 자발적 은퇴, 아니면 45세 강제 은퇴
 *       (레전드는 강등되지 않으므로 출전 여부만으로는 은퇴 시점을 알 수 없다)</li>
 * </ul>
 */
@Tag("career")
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CareerBalanceTest {

    static final int VOLUNTARY_AGE = 35;
    static final int MAX_SEASONS = 30;

    static final Set<AwardType> MONTHLY = EnumSet.of(AwardType.PLAYER_OF_MONTH, AwardType.PITCHER_OF_MONTH, AwardType.KBO_MONTHLY_MVP,
            AwardType.ROOKIE_OF_MONTH, AwardType.RELIEVER_OF_MONTH);
    static final Set<AwardType> TOP = EnumSet.of(AwardType.MVP, AwardType.CY_YOUNG, AwardType.KBO_MVP);
    static final Set<AwardType> ALL_STAR = EnumSet.of(AwardType.ALL_STAR, AwardType.KBO_ALL_STAR);
    static final Set<AwardType> SEASON = EnumSet.of(AwardType.ROOKIE_OF_YEAR, AwardType.SILVER_SLUGGER,
            AwardType.GOLD_GLOVE, AwardType.RELIEVER_OF_YEAR);
    static final Set<AwardType> MINORS = EnumSet.of(AwardType.MINOR_PLAYER_OF_MONTH, AwardType.MINOR_ALL_STAR,
            AwardType.MINOR_PLAYER_OF_YEAR);

    record Profile(String label, Position position, Education education, LeagueMode mode) {
    }

    static final List<Profile> PROFILES = List.of(
            new Profile("고졸 SS", Position.SS, Education.HIGH_SCHOOL, LeagueMode.MLB),
            new Profile("대졸 SS", Position.SS, Education.COLLEGE, LeagueMode.MLB),
            new Profile("대졸 OF", Position.OF, Education.COLLEGE, LeagueMode.MLB),
            new Profile("고졸 SP", Position.SP, Education.HIGH_SCHOOL, LeagueMode.MLB),
            new Profile("대졸 SP", Position.SP, Education.COLLEGE, LeagueMode.MLB),
            new Profile("대졸 CP", Position.CP, Education.COLLEGE, LeagueMode.MLB),
            // KBO 모드 (K4): 퓨처스 → 1군, 원화
            new Profile("KBO고졸SS", Position.SS, Education.HIGH_SCHOOL, LeagueMode.KBO),
            new Profile("KBO대졸SS", Position.SS, Education.COLLEGE, LeagueMode.KBO),
            new Profile("KBO고졸SP", Position.SP, Education.HIGH_SCHOOL, LeagueMode.KBO));

    record Career(Profile profile, int draftRound, LeagueLevel startLevel, int startAge, Integer debutAge,
                  int majorSeasons, double peakOverall, Map<Integer, Double> overallByAge, Integer maxedAge, long maxSalary, long earnings, int awardPoints,
                  Map<AwardType, Integer> awards, int retireAge, boolean forced, int hallOfFame, String hallLabel,
                  String statLine, long millis) {

        String overallAt(int age) {
            Double value = overallByAge.get(age);
            return value == null ? "-" : String.format("%.1f", value);
        }

        boolean reachedMlb() {
            return debutAge != null;
        }

        int count(Set<AwardType> types) {
            return types.stream().mapToInt(t -> awards.getOrDefault(t, 0)).sum();
        }
    }

    @Autowired PlayerService playerService;
    @Autowired CombineService combineService;
    @Autowired DraftService draftService;
    @Autowired SeasonService seasonService;
    @Autowired TrainingService trainingService;
    @Autowired StatPointService statPointService;
    @Autowired PostseasonService postseasonService;
    @Autowired SeasonTransitionService seasonTransitionService;
    @Autowired ContractService contractService;
    @Autowired CareerService careerService;
    @Autowired UserRepository userRepository;
    @Autowired PlayerRepository playerRepository;

    final StatFormat fmt = new StatFormat();
    final List<Career> careers = new ArrayList<>();

    @BeforeAll
    void simulateAll() {
        int perProfile = Integer.parseInt(System.getProperty("careers", "2"));
        int index = 0;
        for (Profile profile : PROFILES) {
            for (int i = 0; i < perProfile; i++) {
                Career career = simulate(profile, index++);
                careers.add(career);
                System.out.printf("[%d/%d] %s 완료 (%.1f초)%n", careers.size(), PROFILES.size() * perProfile,
                        profile.label(), career.millis() / 1000.0);
            }
        }
        printReport(perProfile);
    }

    // ------------------------------------------------------------ 자동 진행

    private Career simulate(Profile profile, int index) {
        long started = System.currentTimeMillis();
        String username = "cb" + (System.nanoTime() % 1_000_000) + "x" + index;
        Long userId = userRepository.save(new User(username, "{noop}pw", "자동" + index)).getId();
        Player created = playerService.create(userId, form(profile, index), profile.mode());
        Long playerId = created.getId();
        List<Attribute> order = Attribute.forPosition(profile.position());

        combineService.run(userId, playerId, InterviewAnswer.GROWTH);
        DraftResult draft = draftService.run(userId, playerId);

        double peak = created.getOverall();
        long maxSalary = 0;
        Integer maxedAge = null;
        Map<Integer, Double> overallByAge = new java.util.HashMap<>();
        boolean forced = false;
        for (int n = 0; n < MAX_SEASONS; n++) {
            resolveContracts(userId, playerId);
            PlayerSeason season = seasonService.currentSeason(userId, playerId);
            while (!season.isCompleted()) {
                season = seasonService.playNextSeries(userId, playerId).season();
                if (season.isTrainingPending()) {                // 능력치가 모두 100이면 훈련 차례가 없다
                    trainingService.trainAutomatically(userId, playerId, order);
                }
                spendPoints(userId, playerId, order);
            }
            Postseason postseason = postseasonService.getOrStart(userId, playerId);
            while (!postseason.isDone()) {
                postseason = postseasonService.playNextRound(userId, playerId);
            }
            spendPoints(userId, playerId, order);

            Player player = playerRepository.findById(playerId).orElseThrow();
            peak = Math.max(peak, player.getOverall());
            overallByAge.put(player.getAge(), player.getOverall());
            if (maxedAge == null && !player.hasTrainableAttribute()) {
                maxedAge = player.getAge();
            }
            maxSalary = Math.max(maxSalary, player.getSalary());
            int mlbSeries = seasonService.currentSeason(userId, playerId).getMlbSeriesPlayed();
            // 레전드(명예의 전당 150+)는 강등되지 않아 최상위 레벨 출전 기록이 계속 생기므로,
            // 최상위 레벨에서 벤치 신세(주전 기준 −6 미만)가 되면 은퇴하는 것도 자발적 은퇴로 본다
            boolean benchAtTop = LineupRules.standing(player.getOverall(), LeagueRulesets.of(player).topLevel())
                    == LineupRules.Standing.BENCH;
            if (player.getAge() >= VOLUNTARY_AGE && (mlbSeries == 0 || benchAtTop)) {
                careerService.retire(userId, playerId);
                break;
            }
            if (seasonTransitionService.advance(userId, playerId).retired()) {
                forced = true;
                break;
            }
        }

        Player finished = playerRepository.findById(playerId).orElseThrow();
        CareerService.Summary summary = careerService.summary(userId, playerId);
        Integer debutAge = null;
        int firstYear = summary.seasons().get(0).season().getSeasonYear();
        for (CareerService.SeasonRow row : summary.seasons()) {
            if (row.season().getMlbSeriesPlayed() > 0) {
                debutAge = created.getAge() + row.season().getSeasonYear() - firstYear;
                break;
            }
        }
        Map<AwardType, Integer> awards = new EnumMap<>(AwardType.class);
        for (Award award : summary.awards()) {
            awards.merge(award.getType(), 1, Integer::sum);
        }
        return new Career(profile, draft.getRound(), draft.getStartLevel(), created.getAge(), debutAge,
                summary.majorSeasons(), peak, overallByAge, maxedAge, maxSalary, summary.careerEarnings(), summary.awardPoints(), awards,
                finished.getAge(), forced, summary.hallOfFameScore(), summary.getHallOfFameLabel(),
                statLine(finished, summary), System.currentTimeMillis() - started);
    }

    private PlayerCreateForm form(Profile profile, int index) {
        PlayerCreateForm form = new PlayerCreateForm();
        form.setName("자동선수" + index);
        form.setUniformNumber(10 + index % 80);
        form.setPosition(profile.position());
        form.setBats(Hand.R);
        form.setThrowsHand(Hand.R);
        form.setNationality(Nationality.USA);
        form.setEducation(profile.education());
        form.setSchoolRegion(SchoolRegion.US_EAST);
        Map<Attribute, Integer> allocation = new EnumMap<>(Attribute.class);
        Attribute.forPosition(profile.position()).forEach(a -> allocation.put(a, 5));   // 55포인트 균등 배분
        form.setAllocation(allocation);
        if (profile.position().isPitcher()) {
            form.setPitch2(PitchType.SLIDER);
            form.setPitch3(PitchType.CHANGEUP);
            form.setPitch4(PitchType.CURVE);
        }
        return form;
    }

    /** 연장 제안 우선, 아니면 총액이 가장 큰 제안 수락 (수락하면 같은 오프시즌의 다른 제안은 소멸) */
    private void resolveContracts(Long userId, Long playerId) {
        for (List<ContractOffer> offers = contractService.pending(playerId); !offers.isEmpty();
             offers = contractService.pending(playerId)) {
            contractService.accept(userId, playerId, pickOffer(offers).getId());
        }
    }

    private static ContractOffer pickOffer(List<ContractOffer> offers) {
        return offers.stream()
                .filter(o -> o.getKind() == ContractOffer.Kind.EXTENSION)
                .findFirst()
                .orElseGet(() -> offers.stream()
                        .max(Comparator.comparingLong(o -> o.getSalary() * o.getYears()))
                        .orElseThrow());
    }

    private void spendPoints(Long userId, Long playerId, List<Attribute> order) {
        Player player = playerRepository.findById(playerId).orElseThrow();
        int spent = 0;
        while (player.getStatPoints() > 0 && player.hasTrainableAttribute()) {
            statPointService.spend(userId, playerId, AutoTrainingOrder.choose(player, order, spent++));
            player = playerRepository.findById(playerId).orElseThrow();
        }
    }

    private String statLine(Player player, CareerService.Summary summary) {
        if (player.isPitcher()) {
            PitchingLine p = summary.mlbPitching();
            return p.games == 0 ? "-" : p.wins + "승 " + p.saves + "S " + p.strikeouts + "K ERA " + fmt.two(p.getEra());
        }
        BattingLine b = summary.mlbBatting();
        return b.games == 0 ? "-" : b.hits + "안타 " + b.homeRuns + "HR " + fmt.rate(b.getAverage())
                + "/" + fmt.rate(b.getObp()) + "/" + fmt.rate(b.getSlg());
    }

    // ------------------------------------------------------------ 리포트

    private void printReport(int perProfile) {
        System.out.println();
        System.out.println("===== 커리어별 결과 =====");
        System.out.printf("%-7s %3s %-5s %4s %4s %4s %6s %5s %5s %5s %13s %14s %5s %4s %4s %4s %4s %6s %5s  %s%n",
                "프로필", "R", "시작", "나이", "데뷔", "MLB", "최고OVR", "30세", "35세", "40세", "최고연봉", "누적수입",
                "수상P", "월간", "올스", "MVP", "은퇴", "명전", "강제", "MLB 통산");
        for (Career c : careers) {
            System.out.printf("%-7s %3d %-5s %4d %4s %4d %6.1f %5s %5s %5s %13s %14s %5d %4d %4d %4d %4d %6d %5s  %s%n",
                    c.profile().label(), c.draftRound(), c.startLevel().name(), c.startAge(),
                    c.reachedMlb() ? c.debutAge() : "-", c.majorSeasons(), c.peakOverall(),
                    c.overallAt(30), c.overallAt(35), c.overallAt(40),
                    fmt.money(c.maxSalary(), c.profile().mode()), fmt.money(c.earnings(), c.profile().mode()), c.awardPoints(),
                    c.count(MONTHLY), c.count(ALL_STAR), c.count(TOP),
                    c.retireAge(), c.hallOfFame(), c.forced() ? "45세" : "자발", c.statLine());
        }

        System.out.println();
        System.out.println("===== 프로필별 요약 (프로필당 " + perProfile + "커리어) =====");
        System.out.printf("%-7s %6s %6s %6s %6s %6s %14s %13s %6s %6s %6s %6s %6s %6s%n",
                "프로필", "MLB도달", "데뷔중앙", "MLB시즌", "최고OVR", "올100%", "누적수입중앙", "최고연봉중앙",
                "수상P", "월간", "올스타", "MVP/CY", "은퇴나이", "명전후보");
        for (Profile profile : PROFILES) {
            List<Career> group = careers.stream().filter(c -> c.profile() == profile).toList();
            printSummaryRow(profile.label(), group);
        }
        for (LeagueMode mode : LeagueMode.values()) {
            printSummaryRow(mode.getLabel() + "전체", careers.stream().filter(c -> c.profile().mode() == mode).toList());
        }
        System.out.println();
    }

    private void printSummaryRow(String label, List<Career> group) {
        List<Career> reached = group.stream().filter(Career::reachedMlb).toList();
        System.out.printf("%-7s %5.0f%% %6s %6.1f %6.1f %5.0f%% %14s %13s %6.1f %6.1f %6.1f %6.2f %6.1f %5.0f%%%n",
                label,
                rate(group, Career::reachedMlb),
                reached.isEmpty() ? "-" : fmt.one(median(reached, c -> c.debutAge())),
                average(group, Career::majorSeasons),
                average(group, Career::peakOverall),
                rate(group, c -> c.maxedAge() != null),
                fmt.money((long) median(group, c -> c.earnings()), mode(group)),
                fmt.money((long) median(group, c -> c.maxSalary()), mode(group)),
                average(group, Career::awardPoints),
                average(group, c -> c.count(MONTHLY)),
                average(group, c -> c.count(ALL_STAR)),
                average(group, c -> c.count(TOP)),
                average(group, Career::retireAge),
                rate(group, c -> c.hallLabel() != null));
    }

    static double median(List<Career> group, ToDoubleFunction<Career> value) {
        double[] sorted = group.stream().mapToDouble(value).sorted().toArray();
        if (sorted.length == 0) return 0;
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    static double average(List<Career> group, ToDoubleFunction<Career> value) {
        return group.stream().mapToDouble(value).average().orElse(0);
    }

    /** 금액 표시 통화 (그룹은 한 모드씩 묶는다) */
    static LeagueMode mode(List<Career> group) {
        return group.isEmpty() ? LeagueMode.MLB : group.get(0).profile().mode();
    }

    static double rate(List<Career> group, Predicate<Career> condition) {
        return group.isEmpty() ? 0 : 100.0 * group.stream().filter(condition).count() / group.size();
    }

    // ------------------------------------------------------------ 검증 (넓은 범위 — 리포트를 보고 좁혀 간다)

    @Test
    void 모든_커리어가_은퇴로_끝나고_은퇴_나이가_정책_범위다() {
        assertThat(careers).allSatisfy(c -> {
            assertThat(c.retireAge()).isBetween(VOLUNTARY_AGE, 45);
            assertThat(c.forced()).isEqualTo(c.retireAge() == 45);
        });
    }

    @Test
    void 대부분_메이저에_데뷔하고_데뷔_나이는_20대다() {
        assertThat(rate(careers, Career::reachedMlb)).isGreaterThanOrEqualTo(50);
        List<Career> reached = careers.stream().filter(Career::reachedMlb).toList();
        assertThat(median(reached, c -> c.debutAge())).isBetween(20.0, 28.0);
    }

    @Test
    void 최상위_레벨_선수의_누적_수입은_모드별_적정_범위다() {
        List<Career> reached = careers.stream()
                .filter(c -> c.majorSeasons() >= 3 && c.profile().mode() == LeagueMode.MLB).toList();
        if (!reached.isEmpty()) {
            assertThat(median(reached, c -> c.earnings())).isBetween(3_000_000.0, 500_000_000.0);
        }
        // KBO 1군 3시즌 이상: 수억 ~ 수백억 원
        List<Career> kbo = careers.stream()
                .filter(c -> c.majorSeasons() >= 3 && c.profile().mode() == LeagueMode.KBO).toList();
        if (!kbo.isEmpty()) {
            assertThat(median(kbo, c -> c.earnings())).isBetween(300_000_000.0, 50_000_000_000.0);
        }
    }

    @Test
    void 최고_시상은_흔하지_않다() {
        // 자동 진행은 매 시리즈 빠짐없이 훈련하는 최적 경로라 전원이 명예의 전당급(최고 OVR 85~93)으로 큰다.
        // 그래서 커리어당 횟수 대신 메이저 시즌 대비 비율로 본다 — 역대 최고(본즈 7회/22시즌 ≈ 0.32) 수준을 넘으면 너무 쉽다
        double perSeason = average(careers.stream().filter(c -> c.majorSeasons() > 0).toList(),
                c -> (double) c.count(TOP) / c.majorSeasons());
        assertThat(perSeason).as("메이저 시즌당 MVP·사이영").isLessThan(0.35);
        assertThat(careers).allSatisfy(c -> assertThat(c.count(TOP)).as(c.profile().label()).isLessThanOrEqualTo(10));
    }

    @Test
    void 대부분_전성기가_지나면_스스로_은퇴한다() {
        // 노화가 약하면 전원이 45세 강제 은퇴까지 정상급을 유지한다 (M5-3 조정 전 상태)
        assertThat(rate(careers, c -> !c.forced())).isGreaterThanOrEqualTo(50);
    }
}
