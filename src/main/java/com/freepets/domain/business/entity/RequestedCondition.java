package com.freepets.domain.business.entity;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.global.util.JsonListUtil;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사업자가 신청서에 적어 낸 출입 조건. 승인되기 전까지 여기에 보관했다가, 운영자가 승인할 때 시설에 반영한다
 * ({@code Facility.confirmByOwner}). 심사 중인 신청이 시설 정보를 바꾸면 승인 절차를 둔 의미가 없다.
 *
 * <p>승인 절차 이전에 만들어진 기록에는 이 값이 없어 모든 컬럼이 nullable이다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestedCondition {

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_pet_allowed", length = 20)
    private PetAllowed petAllowed;

    /** 동반 가능한 최대 체중(kg). 상한이 있을 때만 채워진다. */
    @Column(name = "requested_max_weight", precision = 5, scale = 2)
    private BigDecimal maxWeight;

    /** {@code true}="이하", {@code false}="미만". 최대 체중이 없으면 이 값도 항상 {@code null}이다. */
    @Column(name = "requested_max_weight_inclusive")
    private Boolean maxWeightInclusive;

    /** 저장은 JSON 문자열, 읽기는 {@link #getRequirements()} — Lombok 기본 getter는 끈다(Facility.requiredItems와 같은 방식). */
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_requirements", columnDefinition = "json")
    private String requirements;

    /** 화면에 그대로 보여줄 조건 안내문. */
    @Column(name = "requested_condition_raw", columnDefinition = "TEXT")
    private String conditionRaw;

    private RequestedCondition(
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            Boolean maxWeightInclusive,
            String requirements,
            String conditionRaw
    ) {
        this.petAllowed = petAllowed;
        this.maxWeight = maxWeight;
        this.maxWeightInclusive = maxWeightInclusive;
        this.requirements = requirements;
        this.conditionRaw = conditionRaw;
    }

    public static RequestedCondition of(
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            Boolean maxWeightInclusive,
            List<Requirement> requirements,
            String conditionRaw
    ) {
        return new RequestedCondition(
                petAllowed,
                maxWeight,
                // 상한이 없으면 경계 종류("이하"/"미만")도 의미가 없다(Facility.confirmByOwner와 같은 규칙).
                maxWeight == null ? null : maxWeightInclusive,
                JsonListUtil.toJson(distinctNames(requirements)),
                conditionRaw
        );
    }

    public List<Requirement> getRequirements() {
        return JsonListUtil.fromJson(requirements).stream()
                .map(Requirement::valueOf)
                .toList();
    }

    /** 같은 조건이 여러 번 오면 승인 시 체크리스트에 중복 행이 쌓이므로 걸러낸다. */
    private static List<String> distinctNames(List<Requirement> requirements) {
        return requirements == null
                ? List.of()
                : requirements.stream().distinct().map(Requirement::name).toList();
    }
}
