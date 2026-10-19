package com.mlbgame.player;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.common.Messages;
import com.mlbgame.contract.ContractType;
import com.mlbgame.league.LeagueLevel;
import com.mlbgame.league.Team;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.MapKeyJdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "players")
public class Player extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "uniform_number", nullable = false)
    private int uniformNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Position position;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "bats_hand", nullable = false, length = 5)
    private Hand bats;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "throws_hand", nullable = false, length = 5)
    private Hand throwsHand;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Education education;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Nationality nationality;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "school_region", nullable = false, length = 20)
    private SchoolRegion schoolRegion;

    @Column(nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PlayerStage stage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PlayerStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "league_level", length = 10)
    private LeagueLevel leagueLevel;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id")
    private Team team;

    // 선수당 11행뿐이라 EAGER 로 함께 읽는다 (open-in-view=false 에서 화면 렌더링 시 지연 로딩 예외 방지)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_attributes", joinColumns = @JoinColumn(name = "player_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyJdbcTypeCode(SqlTypes.VARCHAR)
    @MapKeyColumn(name = "attr_code", length = 30)
    @Column(name = "attr_value", nullable = false)
    private Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);

    /** 다음 +1 까지 쌓인 훈련 횟수 ({@link TrainingCost}). 0 이면 행 없음 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_training_progress", joinColumns = @JoinColumn(name = "player_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyJdbcTypeCode(SqlTypes.VARCHAR)
    @MapKeyColumn(name = "attr_code", length = 30)
    @Column(name = "progress", nullable = false)
    private Map<Attribute, Integer> trainingProgress = new EnumMap<>(Attribute.class);

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_pitches", joinColumns = @JoinColumn(name = "player_id"))
    @MapKeyColumn(name = "slot")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "pitch_type", nullable = false, length = 20)
    private Map<Integer, PitchType> pitches = new HashMap<>();

    /** 메이저 계약 연봉 (달러/년). 마이너 계약이면 0 */
    @Column(nullable = false)
    private long salary;

    /** 남은 계약 연수 (이번 시즌 포함) */
    @Column(name = "contract_years", nullable = false)
    private int contractYears;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "contract_type", nullable = false, length = 20)
    private ContractType contractType = ContractType.NONE;

    /** 보유 스탯업 포인트 (시상으로 적립, 시즌 넘어 이월) */
    @Column(name = "stat_points", nullable = false)
    private int statPoints;

    /** 평판 0~100 (시작 50). 규칙: {@code ReputationRules} */
    @Column(nullable = false)
    private int reputation = 50;

    /** 현재 계약의 트레이드 거부 조항 — 있으면 구단 주도 트레이드가 없다 (직접 요청한 트레이드는 가능) */
    @Column(name = "no_trade_clause", nullable = false)
    private boolean noTradeClause;

    @Version
    private Long version;

    protected Player() {
    }

    /** 새 계약 (FA 이적이면 구단도 바뀐다). 트레이드 거부 조항 없음 */
    public void signContract(Team team, long salary, int years, ContractType type) {
        signContract(team, salary, years, type, false);
    }

    /** 새 계약 — 트레이드 거부 조항은 이 계약 기준으로 다시 정해진다 */
    public void signContract(Team team, long salary, int years, ContractType type, boolean noTradeClause) {
        if (team != null) {
            this.team = team;
        }
        this.salary = salary;
        this.contractYears = years;
        this.contractType = type;
        this.noTradeClause = noTradeClause;
    }

    /** 트레이드: 계약은 그대로, 구단만 바뀐다 (레벨 유지) */
    public void tradeTo(Team newTeam) {
        requireStage(PlayerStage.DRAFTED);
        this.team = newTeam;
    }

    /** 직접 요청한 트레이드가 성사되면 거부 조항은 스스로 포기한 것으로 보고 소멸 */
    public void waiveNoTradeClause() {
        this.noTradeClause = false;
    }

    public boolean isNoTradeClause() {
        return noTradeClause;
    }

    public int getReputation() {
        return reputation;
    }

    /**
     * 평판 변화 (0~100 범위로 자른다).
     *
     * @return 실제로 바뀐 양 (범위 끝이면 요청보다 작을 수 있다)
     */
    public int changeReputation(int delta) {
        int before = reputation;
        reputation = Math.max(0, Math.min(100, reputation + delta));
        return reputation - before;
    }

    /** 다년 계약 1년 경과 */
    public void passContractYear() {
        this.contractYears = Math.max(0, contractYears - 1);
    }

    public long getSalary() {
        return salary;
    }

    public int getContractYears() {
        return contractYears;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public void addStatPoints(int points) {
        this.statPoints += points;
    }

    /** 포인트 1개를 써서 능력치 +1 */
    public int spendStatPoint(Attribute attribute) {
        if (statPoints <= 0) {
            throw new IllegalStateException(Messages.get("error.noStatPoints"));
        }
        int value = raise(attribute);
        statPoints--;
        return value;
    }

    public int getStatPoints() {
        return statPoints;
    }

    /**
     * 새 선수 생성. 능력치 = 학력별 기본값(모두 동일) + 유저가 자유 배분한 포인트. 랜덤 없음.
     * 배분 규칙(총량·능력치당 한도) 검증은 {@link PlayerService} 가 먼저 한다.
     *
     * @param allocation     능력치별 배분 포인트 (없는 능력치는 0)
     * @param pitchSelection 투수일 때 포심 외에 고른 구종 3개 (슬롯 2~4로 저장, 야수는 빈 리스트)
     */
    public static Player create(Long userId, String name, int uniformNumber, Position position, Hand bats,
                                Hand throwsHand, Nationality nationality, Education education,
                                SchoolRegion schoolRegion, int startValue, Map<Attribute, Integer> allocation,
                                List<PitchType> pitchSelection) {
        Player player = new Player();
        player.userId = userId;
        player.name = name;
        player.uniformNumber = uniformNumber;
        player.position = position;
        player.bats = bats;
        player.throwsHand = throwsHand;
        player.nationality = nationality;
        player.education = education;
        player.schoolRegion = schoolRegion;
        player.age = education.getStartAge();
        player.stage = PlayerStage.CREATED;
        player.status = PlayerStatus.ACTIVE;
        for (Attribute attribute : Attribute.forPosition(position)) {
            player.attributes.put(attribute, startValue + allocation.getOrDefault(attribute, 0));
        }
        if (position.isPitcher()) {
            for (int i = 0; i < pitchSelection.size(); i++) {
                player.pitches.put(i + 2, pitchSelection.get(i));
            }
        }
        return player;
    }

    public void completeCombine() {
        requireStage(PlayerStage.CREATED);
        this.stage = PlayerStage.COMBINE_DONE;
    }

    public void draft(Team team, LeagueLevel startLevel) {
        requireStage(PlayerStage.COMBINE_DONE);
        this.team = team;
        this.leagueLevel = startLevel;
        this.stage = PlayerStage.DRAFTED;
    }

    /** 시즌 전환: 나이 +1 */
    public void ageOneYear() {
        this.age++;
    }

    /**
     * 노화 하락 적용 (최소 1). 능력치·나이가 바뀌어 훈련 비용이 쌓인 진행도 이하로 줄면
     * 그 자리에서 +1 하고 남은 진행도는 이월한다 (예: 6/8 쌓았는데 비용이 4가 되면 +1, 2/새 비용).
     *
     * @return 정산 내역 문장 (없으면 빈 목록)
     */
    public List<String> applyAging(Map<Attribute, Integer> deltas) {
        deltas.forEach((attribute, delta) -> attributes.computeIfPresent(attribute,
                (a, value) -> Math.max(Attribute.MIN, value + delta)));
        return settleTrainingProgress();
    }

    private List<String> settleTrainingProgress() {
        List<String> lines = new ArrayList<>();
        for (Attribute attribute : List.copyOf(trainingProgress.keySet())) {
            int progress = trainingProgress.get(attribute);
            int before = attributes.getOrDefault(attribute, 0);
            while (canTrain(attribute) && progress >= trainingCost(attribute)) {
                progress -= trainingCost(attribute);
                raise(attribute);
            }
            if (progress > 0 && canTrain(attribute)) {
                trainingProgress.put(attribute, progress);
            } else {
                trainingProgress.remove(attribute);
            }
            int after = attributes.getOrDefault(attribute, 0);
            if (after > before) {
                lines.add(Messages.get("training.settled", attribute.getLabel(), after - before, after)
                        + (progress > 0 && canTrain(attribute)
                        ? Messages.get("training.settled.carry", progress, trainingCost(attribute)) : ""));
            }
        }
        return lines;
    }

    /** 은퇴: 슬롯을 반납하고 기록은 보관 */
    public void retire() {
        this.status = PlayerStatus.RETIRED;
    }

    public boolean isRetired() {
        return status == PlayerStatus.RETIRED;
    }

    /** 승격·강등으로 소속 레벨 변경 (같은 구단 안에서) */
    public void moveToLevel(LeagueLevel level) {
        requireStage(PlayerStage.DRAFTED);
        this.leagueLevel = level;
    }

    /**
     * 훈련 1회: 진행도를 1 쌓고, {@link TrainingCost} 만큼 쌓이면 능력치 +1 (랜덤 없음).
     * 포지션 능력치가 아니거나 이미 100이면 불가.
     *
     * @return 훈련 후 능력치 값
     */
    public int train(Attribute attribute) {
        if (!canTrain(attribute)) {
            throw new IllegalArgumentException(Messages.get("error.cannotTrain", attribute.getLabel()));
        }
        int progress = trainingProgress(attribute) + 1;
        if (progress >= trainingCost(attribute)) {      // 노화로 능력치가 내려가 비용이 줄었을 수도 있으니 >=
            trainingProgress.remove(attribute);
            return raise(attribute);
        }
        trainingProgress.put(attribute, progress);
        return attributes.get(attribute);
    }

    /** 능력치 +1 에 필요한 훈련 횟수 (지금 값·나이 기준) */
    public int trainingCost(Attribute attribute) {
        return TrainingCost.of(attribute(attribute), age);
    }

    /** 다음 +1 까지 쌓인 훈련 횟수 */
    public int trainingProgress(Attribute attribute) {
        return trainingProgress.getOrDefault(attribute, 0);
    }

    /** 능력치 바로 +1 — 훈련 비용이 다 찼을 때, 스탯업 포인트 */
    private int raise(Attribute attribute) {
        if (!canTrain(attribute)) {
            throw new IllegalArgumentException(Messages.get("error.cannotRaise", attribute.getLabel()));
        }
        int value = attributes.get(attribute) + 1;
        attributes.put(attribute, value);
        return value;
    }

    public boolean canTrain(Attribute attribute) {
        Integer value = attributes.get(attribute);
        return value != null && value < Attribute.MAX;
    }

    /** 포지션 능력치 중 아직 100이 아닌 게 하나라도 있는가 (모두 100이면 훈련 차례를 건너뛴다) */
    public boolean hasTrainableAttribute() {
        return Attribute.forPosition(position).stream().anyMatch(this::canTrain);
    }

    public void requireStage(PlayerStage expected) {
        if (stage != expected) {
            throw new InvalidStageException(Messages.get("error.invalidStage", stage));
        }
    }

    public int attribute(Attribute attribute) {
        Integer value = attributes.get(attribute);
        if (value == null) {
            throw new IllegalArgumentException(position + " 포지션은 " + attribute + " 능력치가 없습니다.");
        }
        return value;
    }

    /** OVR = 포지션 능력치 11개의 단순 평균 (소수 첫째 자리) */
    public double getOverall() {
        double avg = Attribute.forPosition(position).stream()
                .mapToInt(this::attribute)
                .average()
                .orElse(0);
        return Math.round(avg * 10) / 10.0;
    }

    /** 화면 표시용 능력치 목록 */
    public List<AttributeView> getAttributeViews() {
        return Attribute.forPosition(position).stream()
                .map(a -> new AttributeView(a, a.getLabel(), attribute(a)))
                .toList();
    }

    /**
     * 투수의 구종 목록 (포심 + 생성 시 고른 3개). 능력치가 아닌 캐릭터 설정으로,
     * 하이라이트 문구 연출에만 쓴다. 야수는 빈 리스트.
     */
    public List<PitchType> getPitchRepertoire() {
        if (!isPitcher()) {
            return List.of();
        }
        List<PitchType> repertoire = new ArrayList<>();
        repertoire.add(PitchType.FOUR_SEAM);
        pitches.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> repertoire.add(e.getValue()));
        return repertoire;
    }

    public boolean isPitcher() {
        return position.isPitcher();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public int getUniformNumber() {
        return uniformNumber;
    }

    public Position getPosition() {
        return position;
    }

    public Hand getBats() {
        return bats;
    }

    public Hand getThrowsHand() {
        return throwsHand;
    }

    public Education getEducation() {
        return education;
    }

    public Nationality getNationality() {
        return nationality;
    }

    public SchoolRegion getSchoolRegion() {
        return schoolRegion;
    }

    public int getAge() {
        return age;
    }

    /** 소속 팀 표시명. 마이너리그는 산하팀 실명 대신 "디트로이트 타이거스 AA" 처럼 레벨을 붙인다 */
    public String getTeamDisplayName() {
        if (team == null) {
            return null;
        }
        return leagueLevel == null || leagueLevel.getTeamSuffix().isEmpty()
                ? team.getDisplayName()
                : team.getDisplayName() + " " + leagueLevel.getTeamSuffix();
    }

    public PlayerStage getStage() {
        return stage;
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public LeagueLevel getLeagueLevel() {
        return leagueLevel;
    }

    public Team getTeam() {
        return team;
    }

    /** "우투좌타" 형식 (영어: "B/T: L/R") */
    public String getHandLabel() {
        return Messages.get("player.hands", throwsHand.getLabel(), bats.getLabel(), throwsHand.name(), bats.name());
    }

    public record AttributeView(Attribute attribute, String label, int value) {
    }
}
