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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 반려동물 하나가 시설 하나에 남길 수 있는 만족도는 항상 1건(upsert)이라 유니크 제약을 건다.
@Getter
@Entity
@Table(
        name = "pet_satisfaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pet_satisfaction_pet_facility",
                columnNames = {"pet_id", "facility_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetSatisfaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pet_satisfaction_id")
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
        this.score = roundToOneDecimal(score);
    }

    public void update(float score) {
        this.score = roundToOneDecimal(score);
    }

    // 화면이 슬라이더 값을 소수점 첫째 자리로만 보여줘서, 저장 시점에 맞춰 잘라둔다.
    private static float roundToOneDecimal(float score) {
        return Math.round(score * 10) / 10.0f;
    }

}
