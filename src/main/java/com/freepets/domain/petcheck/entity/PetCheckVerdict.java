package com.freepets.domain.petcheck.entity;

import com.freepets.domain.pet.entity.Pet;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

// 판별 세션(PetCheck) 안의 아이별 결과. db/schema.sql "pet_check_verdicts" 참고.
// 그룹 세션 자체엔 타임스탬프가 없다(PetCheck의 createdAt으로 충분) — BaseEntity 미상속.
@Getter
@Entity
@Table(name = "pet_check_verdicts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetCheckVerdict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verdict_id")
    private Long verdictId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    private PetCheck petCheck;

    // 펫 삭제 시 NULL(판별 기록은 유지) — db/schema.sql fk_verdict_pet ON DELETE SET NULL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PetCheckResult result;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    // 아이별 지켜야 할 조건. 불가(DENIED) 시 빈 배열.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String conditions;

    @Builder
    private PetCheckVerdict(
            Pet pet,
            PetCheckResult result,
            String reason,
            String conditions
    ) {
        this.pet = pet;
        this.result = result;
        this.reason = reason;
        this.conditions = conditions;
    }

    void assignPetCheck(PetCheck petCheck) {
        this.petCheck = petCheck;
    }

}
