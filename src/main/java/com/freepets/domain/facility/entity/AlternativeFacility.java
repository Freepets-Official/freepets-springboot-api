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

@Getter
@Entity
@Table(name = "alternative_facilities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlternativeFacility extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "distance_km", precision = 6, scale = 2, nullable = false)
    private BigDecimal distanceKm;

    @Builder
    private AlternativeFacility(
            Facility facility,
            BigDecimal distanceKm
    ) {
        this.facility = facility;
        this.distanceKm = distanceKm;
    }

}
