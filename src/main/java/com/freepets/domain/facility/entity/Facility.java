package com.freepets.domain.facility.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "facilities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Facility extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facility_id")
    private Long facilityId;

    @Column(length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FacilityCategory category;

    @Column(length = 300, nullable = false)
    private String address;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal lng;

    @Column(length = 30, nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_allowed", nullable = false)
    private PetAllowed petAllowed;

    @Column(name = "pet_condition_raw", columnDefinition = "TEXT", nullable = false)
    private String petConditionRaw;

    @Column(name = "max_weight", precision = 5, scale = 2, nullable = false)
    private BigDecimal maxWeight;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "content_id", length = 20, nullable = false)
    private String contentId;

    @Column(name = "pet_score")
    private Integer petScore;

    @Column(name = "space_rating", nullable = false)
    private float spaceRating;

    @Column(name = "customer_service", nullable = false)
    private float customerService;

    @Column(nullable = false)
    private float amenities;

    @OneToMany(mappedBy = "facility", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CheckList> checkLists = new ArrayList<>();

    @OneToMany(mappedBy = "facility", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlternativeFacility> alternativeFacilities = new ArrayList<>();

    @Builder
    private Facility(
            String name,
            FacilityCategory category,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String phone,
            PetAllowed petAllowed,
            String petConditionRaw,
            BigDecimal maxWeight,
            LocalDateTime checkedAt,
            String contentId,
            Integer petScore,
            float spaceRating,
            float customerService,
            float amenities
    ) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.phone = phone;
        this.petAllowed = petAllowed;
        this.petConditionRaw = petConditionRaw;
        this.maxWeight = maxWeight;
        this.checkedAt = checkedAt;
        this.contentId = contentId;
        this.petScore = petScore;
        this.spaceRating = spaceRating;
        this.customerService = customerService;
        this.amenities = amenities;
    }

}
