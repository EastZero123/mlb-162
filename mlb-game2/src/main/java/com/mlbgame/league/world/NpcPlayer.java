package com.mlbgame.league.world;

import com.mlbgame.player.Attribute;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Position;
import com.mlbgame.simulation.PlayerSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * NPC 선수. 능력치 11개는 포지션별 표시 순서대로 "65,70,58,..." 형태 한 칸에 저장한다 (선수당 1행).
 */
@Entity
@Table(name = "npc_players")
public class NpcPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(nullable = false, length = 40)
    private String name;

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

    @Column(nullable = false)
    private int age;

    @Column(nullable = false)
    private int potential;

    @Column(name = "attribute_values", nullable = false, length = 60)
    private String attributeValues;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "roster_role", nullable = false, length = 10)
    private RosterRole rosterRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private NpcStatus status;

    @Column(nullable = false)
    private long salary;

    @Column(name = "contract_years", nullable = false)
    private int contractYears;

    @Column(name = "service_years", nullable = false)
    private int serviceYears;

    protected NpcPlayer() {
    }

    /** 시즌 전환 중 새로 1군에 올라온 유망주 등 */
    public static NpcPlayer create(Long worldId, Long teamId, String name, Position position, Hand bats, Hand throwsHand,
                                   int age, int potential, int[] attributes, RosterRole role, long salary,
                                   int contractYears, int serviceYears) {
        NpcPlayer npc = new NpcPlayer();
        npc.worldId = worldId;
        npc.teamId = teamId;
        npc.name = name;
        npc.position = position;
        npc.bats = bats;
        npc.throwsHand = throwsHand;
        npc.age = age;
        npc.potential = potential;
        npc.attributeValues = encode(attributes);
        npc.rosterRole = role;
        npc.status = NpcStatus.MLB;
        npc.salary = salary;
        npc.contractYears = contractYears;
        npc.serviceYears = serviceYears;
        return npc;
    }

    // ---- 시즌 전환 (WorldEvolution) ----

    public void ageOneYear() {
        age++;
    }

    public void updateAttributes(int[] values) {
        int[] clamped = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            clamped[i] = Math.max(Attribute.MIN, Math.min(Attribute.MAX, values[i]));
        }
        this.attributeValues = encode(clamped);
    }

    /** 1군 소속·역할 지정 */
    public void assign(Long teamId, RosterRole role) {
        this.teamId = teamId;
        this.rosterRole = role;
        this.status = NpcStatus.MLB;
    }

    /** 1군에서 밀려나 마이너로 (소속 구단 유지, 경기 기록 없음) */
    public void sendDown() {
        this.status = NpcStatus.MINORS;
    }

    /** 새 계약 */
    public void sign(Long teamId, long salary, int years) {
        this.teamId = teamId;
        this.salary = salary;
        this.contractYears = years;
        this.status = NpcStatus.MLB;
    }

    /** 계약 1년 경과. 메이저에 있었으면 서비스 연수 +1 */
    public void passContractYear() {
        if (status == NpcStatus.MLB) {
            serviceYears++;
        }
        contractYears = Math.max(0, contractYears - 1);
    }

    /** 연봉조정·최저 연봉 구간 재계약 (1년) */
    public void renew(long salary) {
        this.salary = salary;
        this.contractYears = 1;
    }

    public void becomeFreeAgent() {
        this.status = NpcStatus.FA;
        this.teamId = null;
    }

    public void retire() {
        this.status = NpcStatus.RETIRED;
        this.teamId = null;
    }

    public static String encode(int[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(values[i]);
        }
        return sb.toString();
    }

    public int[] attributeArray() {
        String[] parts = attributeValues.split(",");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Integer.parseInt(parts[i].trim());
        }
        return values;
    }

    public Map<Attribute, Integer> attributes() {
        List<Attribute> order = Attribute.forPosition(position);
        int[] values = attributeArray();
        Map<Attribute, Integer> map = new EnumMap<>(Attribute.class);
        for (int i = 0; i < order.size(); i++) {
            map.put(order.get(i), values[i]);
        }
        return map;
    }

    public double getOverall() {
        double sum = 0;
        int[] values = attributeArray();
        for (int value : values) sum += value;
        return Math.round(sum / values.length * 10) / 10.0;
    }

    /** 경기 엔진 입력용 스냅샷 (NPC 를 개별 상대로 쓸 때) */
    public PlayerSnapshot snapshot() {
        return new PlayerSnapshot(position, bats, throwsHand, attributes(), List.of());
    }

    public Long getId() {
        return id;
    }

    public Long getWorldId() {
        return worldId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getName() {
        return name;
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

    public int getAge() {
        return age;
    }

    public int getPotential() {
        return potential;
    }

    public RosterRole getRosterRole() {
        return rosterRole;
    }

    public NpcStatus getStatus() {
        return status;
    }

    public long getSalary() {
        return salary;
    }

    public int getContractYears() {
        return contractYears;
    }

    public int getServiceYears() {
        return serviceYears;
    }
}
