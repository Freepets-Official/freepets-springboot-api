package com.freepets.domain.petsatisfaction.entity;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.pet.entity.Pet;

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
@Table(name = "pet_satisfication")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetSatisfaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pet_satisfication_id")
    private Long petSatisfactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(nullable = false)
    private float score;

    @Builder
    private PetSatisfaction(
            Pet pet,
            Facility facility,
            float score
    ) {
        this.pet = pet;
        this.facility = facility;
        this.score = score;
    }

}
