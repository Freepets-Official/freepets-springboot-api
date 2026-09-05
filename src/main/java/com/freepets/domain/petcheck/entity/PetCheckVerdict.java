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

    // 동반 출입증 검증(GET /verify/{code})용 공개 조회 코드. VerifyCodeGenerator로 발급하며,
    // 판별 결과가 곧 개인정보(반려동물 이름·체중·접종여부)라 예측 불가능해야 한다 — checkId·petId를
    // 조합해 역산 가능한 값은 쓰지 않는다. 이 컬럼이 생기기 전 판별 기록은 애초에 출입증 QR도
    // 없었으므로 채울 값이 없다 — nullable로 두고 백필하지 않는다.
    @Column(name = "verify_code", unique = true, length = 20)
    private String verifyCode;

    @Builder
    private PetCheckVerdict(
            Pet pet,
            PetCheckResult result,
            String reason,
            String conditions,
            String verifyCode
    ) {
        this.pet = pet;
        this.result = result;
        this.reason = reason;
        this.conditions = conditions;
        this.verifyCode = verifyCode;
    }

    void assignPetCheck(PetCheck petCheck) {
        this.petCheck = petCheck;
    }

}
