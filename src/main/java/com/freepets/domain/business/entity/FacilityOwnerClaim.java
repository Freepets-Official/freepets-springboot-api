package com.freepets.domain.business.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사업자의 매장 소유 기록. 소유권과 국세청 진위확인 "사실"만 남긴다 — 출입 조건 값은
 * {@link Facility}에 직접 저장한다.
 *
 * <p>사업자 프로필은 따로 저장하지 않고 이 기록에서 파생한다. 한 사용자에게 행이 하나라도 있으면
 * 그 계정에 {@code OWNER} 프로필이 붙는다. 프로필과 소유 매장을 따로 저장하면 "프로필은 있는데
 * 매장이 없는" 계정처럼 둘이 어긋날 수 있어서다.
 */
@Getter
@Entity
@Table(
        name = "facility_owner_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_facility_owner_claims_facility_id",
                columnNames = "facility_id"
        ),
        indexes = @Index(name = "idx_facility_owner_claims_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityOwnerClaim extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_id")
    private Long claimId;

    /** 탈퇴하면 소유 기록도 함께 지워져 {@code OWNER} 프로필이 저절로 사라진다. 시설은 남는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    /** 한 시설은 한 사업자만 소유한다(UNIQUE). 한 사업자는 여러 시설을 가질 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Facility facility;

    /** 마스킹된 사업자등록번호(예: {@code 123-45-*****}). 원본은 저장하지 않는다. */
    @Column(name = "masked_business_number", nullable = false, length = 20)
    private String maskedBusinessNumber;

    /** 국세청 진위확인을 통과한 시각. */
    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Builder
    private FacilityOwnerClaim(
            User user,
            Facility facility,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt
    ) {
        this.user = user;
        this.facility = facility;
        this.maskedBusinessNumber = maskedBusinessNumber;
        this.verifiedAt = verifiedAt;
    }

    /**
     * 요청자가 이 매장의 주인인지. 사업자 기능을 써도 되는지는 요청마다 이 기록으로 확인한다 —
     * 프로필은 화면 세트일 뿐 권한이 아니다.
     */
    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

}
