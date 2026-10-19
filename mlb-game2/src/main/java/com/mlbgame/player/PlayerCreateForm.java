package com.mlbgame.player;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlayerCreateForm {

    @NotBlank(message = "{create.name.required}")
    @Pattern(regexp = "^[가-힣a-zA-Z][가-힣a-zA-Z .'-]{0,18}[가-힣a-zA-Z.]$",
            message = "{create.name.pattern}")
    private String name;

    @NotNull(message = "{create.number.required}")
    @Min(value = 0, message = "{create.number.range}")
    @Max(value = 99, message = "{create.number.range}")
    private Integer uniformNumber;

    @NotNull(message = "{create.position.required}")
    private Position position;

    @NotNull(message = "{create.bats.required}")
    private Hand bats;

    @NotNull(message = "{create.throws.required}")
    private Hand throwsHand;

    @NotNull(message = "{create.nationality.required}")
    private Nationality nationality;

    @NotNull(message = "{create.education.required}")
    private Education education;

    @NotNull(message = "{create.schoolRegion.required}")
    private SchoolRegion schoolRegion;

    // 능력치 자유 배분 (요청 파라미터 allocation[CONTACT]=10 형식, 빈 값은 0)
    private Map<Attribute, Integer> allocation = new EnumMap<>(Attribute.class);

    // 투수 전용: 구종 2~4
    private PitchType pitch2;
    private PitchType pitch3;
    private PitchType pitch4;

    @AssertTrue(message = "{create.position.invalid}")
    public boolean isPositionSelectable() {
        return position == null || position.isUserSelectable();
    }

    @AssertTrue(message = "{create.throws.invalid}")
    public boolean isThrowsHandValid() {
        return throwsHand != Hand.S;
    }

    @AssertTrue(message = "{create.pitches.invalid}")
    public boolean isPitchSelectionValid() {
        if (position == null || !position.isPitcher()) {
            return true;
        }
        List<PitchType> selected = Arrays.asList(pitch2, pitch3, pitch4);
        return selected.stream().allMatch(p -> p != null && p.isSelectable())
                && new HashSet<>(selected).size() == 3;
    }

    /** 투수면 선택한 구종 3개, 야수면 빈 리스트 */
    public List<PitchType> pitchSelection() {
        if (position == null || !position.isPitcher()) {
            return List.of();
        }
        return List.of(Objects.requireNonNull(pitch2), Objects.requireNonNull(pitch3), Objects.requireNonNull(pitch4));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getUniformNumber() {
        return uniformNumber;
    }

    public void setUniformNumber(Integer uniformNumber) {
        this.uniformNumber = uniformNumber;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Hand getBats() {
        return bats;
    }

    public void setBats(Hand bats) {
        this.bats = bats;
    }

    public Hand getThrowsHand() {
        return throwsHand;
    }

    public void setThrowsHand(Hand throwsHand) {
        this.throwsHand = throwsHand;
    }

    public Nationality getNationality() {
        return nationality;
    }

    public void setNationality(Nationality nationality) {
        this.nationality = nationality;
    }

    public SchoolRegion getSchoolRegion() {
        return schoolRegion;
    }

    public void setSchoolRegion(SchoolRegion schoolRegion) {
        this.schoolRegion = schoolRegion;
    }

    public Education getEducation() {
        return education;
    }

    public void setEducation(Education education) {
        this.education = education;
    }

    public Map<Attribute, Integer> getAllocation() {
        return allocation;
    }

    public void setAllocation(Map<Attribute, Integer> allocation) {
        this.allocation = allocation;
    }

    /** 포지션 능력치만 남기고 빈 값은 0으로 정리한 배분 */
    public Map<Attribute, Integer> allocationFor(Position position) {
        Map<Attribute, Integer> result = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.forPosition(position)) {
            Integer points = allocation == null ? null : allocation.get(attribute);
            result.put(attribute, points == null ? 0 : points);
        }
        return result;
    }

    public PitchType getPitch2() {
        return pitch2;
    }

    public void setPitch2(PitchType pitch2) {
        this.pitch2 = pitch2;
    }

    public PitchType getPitch3() {
        return pitch3;
    }

    public void setPitch3(PitchType pitch3) {
        this.pitch3 = pitch3;
    }

    public PitchType getPitch4() {
        return pitch4;
    }

    public void setPitch4(PitchType pitch4) {
        this.pitch4 = pitch4;
    }
}
