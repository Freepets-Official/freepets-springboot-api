package com.freepets.domain.petcheck.entity;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "pet_checks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetCheck extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id")
    private Long checkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Enumerated(EnumType.STRING)
    private PetCheckResult result;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "json")
    private String conditions;

    @Column(columnDefinition = "json")
    private String checklist;

    @Column(length = 50)
    private String model;

    @Builder
    private PetCheck(
            Pet pet,
            User user,
            Facility facility,
            PetCheckResult result,
            String reason,
            String conditions,
            String checklist,
            String model
    ) {
        this.pet = pet;
        this.user = user;
        this.facility = facility;
        this.result = result;
        this.reason = reason;
        this.conditions = conditions;
        this.checklist = checklist;
        this.model = model;
    }

}
