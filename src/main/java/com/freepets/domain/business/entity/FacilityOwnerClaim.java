package com.freepets.domain.business.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사업자의 매장 소유 기록. 소유권과 국세청 진위확인 "사실"만 남긴다 — 출입 조건 값은
 * {@link Facility}에 직접 저장한다.
 *
 * <p>사업자 프로필은 따로 저장하지 않고 이 기록에서 파생한다. 한 사용자에게 <b>승인된</b> 행이 하나라도
 * 있으면 그 계정에 {@code OWNER} 프로필이 붙는다. 프로필과 소유 매장을 따로 저장하면 "프로필은 있는데
 * 매장이 없는" 계정처럼 둘이 어긋날 수 있어서다.
 *
 * <p>"한 시설은 승인된 사업자 하나만"은 조건부 유니크 인덱스라 JPA로 표현할 수 없어 DB에 직접 건다
 * ({@code db/pending-manual-migrations.sql}). 대기 중인 신청은 한 시설에 여러 개 있을 수 있다.
 */
@Getter
@Entity
@Table(
        name = "facility_owner_claims",
        indexes = {
                @Index(name = "idx_facility_owner_claims_user_id", columnList = "user_id"),
                @Index(name = "idx_facility_owner_claims_facility_id", columnList = "facility_id")
        }
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

    /** 한 시설은 승인된 사업자 하나만 소유한다. 한 사업자는 여러 시설을 가질 수 있다. */
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

    // 기본값은 승인 절차 이전에 만들어진 기록을 채우기 위한 것이다. 그 기록들은 국세청 확인만으로 곧바로
    // 소유권을 받았다. 새 신청은 아래 생성자대로 PENDING으로 시작한다.
    @ColumnDefault("'APPROVED'")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    /** 신청서에 적어 낸 출입 조건. 승인될 때 시설에 반영한다. */
    @Embedded
    private RequestedCondition requestedCondition;

    /**
     * 사업자등록증 파일 URL. 운영자가 등록증의 상호·소재지를 신청한 매장과 대조하는 데 쓴다.
     *
     * <p>민감 문서라 관리자 응답에만 내린다({@code S3ImageService.uploadDocument} 참고).
     */
    @Column(name = "registration_certificate_url", columnDefinition = "TEXT")
    private String registrationCertificateUrl;

    @Builder
    private FacilityOwnerClaim(
            User user,
            Facility facility,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt,
            RequestedCondition requestedCondition,
            String registrationCertificateUrl
    ) {
        this.user = user;
        this.facility = facility;
        this.maskedBusinessNumber = maskedBusinessNumber;
        this.verifiedAt = verifiedAt;
        this.requestedCondition = requestedCondition;
        this.registrationCertificateUrl = registrationCertificateUrl;
        // 신청은 운영자 승인을 기다린다. 빌더로 받지 않는다 — 호출부가 임의 상태로 기록을 만드는 경로를 두지 않는다.
        this.status = ClaimStatus.PENDING;
    }

    /**
     * 이 기록을 낸 사람이 요청자인지. <b>승인 여부는 보지 않는다</b> — 대기 신청에도 쓰이므로 이것만으로
     * 매장의 주인이라고 볼 수 없다. 주인인지는 승인된 기록을 조회해서 판단한다.
     */
    public boolean isRequestedBy(Long userId) {
        return user.getId().equals(userId);
    }

}
