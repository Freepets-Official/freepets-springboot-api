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

    /** 반려·해제 사유. 승인에는 쓰지 않는다. */
    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 심사한 관리자 userId. User 연관관계는 두지 않는다 — 이 값을 화면에서 관리자 정보로 역참조할 일이 없다. */
    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

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
     * 국세청 진위확인만으로 곧바로 소유권을 확정하는 경로(신규 매장 자체 등록) 전용이다. 대조할 관광공사
     * 데이터가 없어 등록증 대조·관리자 심사 자체를 두지 않으므로, {@code requestedCondition}·
     * {@code registrationCertificateUrl}은 남기지 않는다 — 출입 조건은 {@link Facility#confirmByOwner}로
     * 시설에 바로 반영되기 때문이다.
     *
     * <p>private 생성자가 강제하는 {@code PENDING} 시작 불변식은 그대로 두고, 생성 직후 이 팩토리가 명시적으로
     * {@link #approve}를 태우는 2단계로 즉시 승인한다 — 빌더로 임의 상태를 만드는 경로를 새로 열지 않는다.
     * {@code adminUserId}를 {@code null}로 넘겨 {@code reviewedByUserId}가 비는 것은 "관리자가 아니라
     * 국세청 인증 통과로 시스템이 즉시 승인했다"는 뜻이다.
     */
    public static FacilityOwnerClaim createApproved(
            User user,
            Facility facility,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt
    ) {
        FacilityOwnerClaim claim = new FacilityOwnerClaim(user, facility, maskedBusinessNumber, verifiedAt, null, null);
        claim.approve(null);
        return claim;
    }

    /**
     * 이 기록을 낸 사람이 요청자인지. <b>승인 여부는 보지 않는다</b> — 대기 신청에도 쓰이므로 이것만으로
     * 매장의 주인이라고 볼 수 없다. 주인인지는 승인된 기록을 조회해서 판단한다.
     */
    public boolean isRequestedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /**
     * 신청을 승인한다. 전이 가능 여부(대기 상태인지)는 서비스 레이어가 조회 직후에 판단하고 여기서는
     * 다시 확인하지 않는다 — 이 코드베이스의 다른 엔티티도 같은 방식이다.
     */
    public void approve(Long adminUserId) {
        this.status = ClaimStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedByUserId = adminUserId;
    }

    public void reject(
            String reason,
            Long adminUserId
    ) {
        this.status = ClaimStatus.REJECTED;
        this.reviewReason = reason;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedByUserId = adminUserId;
    }

    /** 승인된 소유권을 해제한다(이의 제기 처리). 행을 지우지 않고 상태만 바꿔 이력을 남긴다. */
    public void revoke(
            String reason,
            Long adminUserId
    ) {
        this.status = ClaimStatus.REVOKED;
        this.reviewReason = reason;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedByUserId = adminUserId;
    }

}
