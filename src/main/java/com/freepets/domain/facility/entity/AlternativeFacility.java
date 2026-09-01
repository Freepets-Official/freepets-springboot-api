package com.freepets.domain.facility.entity;

import java.math.BigDecimal;

import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code facility}(막힌/원래 시설)의 대안으로 {@code alternativeFacility}를 제안한다.
 *
 * <p>원래는 {@code facility} + {@code distanceKm}뿐이라 "무엇의 대안인지"를 저장할 방법이 없었다
 * (대안 시설 자체를 가리키는 컬럼이 없었음) — {@code alternativeFacility}를 추가해 이 시설 쌍을
 * 실제로 표현할 수 있게 했다. 코스 일괄 검증(`POST /api/v1/ai/course-check`)이 이 테이블을 쓴다.
 */
@Getter
@Entity
@Table(name = "alternative_facilities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlternativeFacility extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 막혀서 대안이 필요해진 원래 시설. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    /** 대안으로 제안하는 시설. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternative_facility_id", nullable = false)
    private Facility alternativeFacility;

    @Column(name = "distance_km", precision = 6, scale = 2, nullable = false)
    private BigDecimal distanceKm;

    @Builder
    private AlternativeFacility(
            Facility facility,
            Facility alternativeFacility,
            BigDecimal distanceKm
    ) {
        this.facility = facility;
        this.alternativeFacility = alternativeFacility;
        this.distanceKm = distanceKm;
    }

}
