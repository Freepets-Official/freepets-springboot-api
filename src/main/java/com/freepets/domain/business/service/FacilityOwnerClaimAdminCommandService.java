package com.freepets.domain.business.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.entity.RequestedCondition;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.event.FacilityBecameIneligibleEvent;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자의 매장 등록 신청 승인·반려·해제. 자영업자 self-service용 {@link FacilityOwnerClaimCommandService}와는
 * 호출 주체(관리자)와 권한이 달라 클래스를 분리한다.
 *
 * <p><b>락 순서</b>: 신청(claim) 행을 먼저 잠그고, 시설을 건드리는 메서드(승인·해제)는 그다음 시설 행을 잠근다.
 * 두 메서드가 서로 다른 순서로 잠그면 교착 상태가 생길 수 있어 순서를 고정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FacilityOwnerClaimAdminCommandService {

    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;
    private final FacilityRepository facilityRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 신청을 승인한다. 신청서에 보관해뒀던 조건을 시설에 반영하고({@link Facility#confirmByOwner}),
     * 그 순간부터 소유권과 사업자 프로필이 파생된다.
     *
     * <p>같은 매장의 다른 대기 신청은 자동 반려하지 않기로 했다 — 이미 승인된 소유자가 있으면 관리자가
     * 그 신청부터 반려해야 한다({@code BUSINESS4003}).
     */
    public BusinessResponseDTO.AdminClaimActionResult approve(
            Long adminUserId,
            Long claimId
    ) {
        FacilityOwnerClaim claim = findClaimForUpdate(claimId);
        requireStatus(claim, ClaimStatus.PENDING);

        Facility facility = facilityRepository.findByIdForUpdate(claim.getFacility().getFacilityId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        facilityOwnerClaimRepository.findApprovedByFacilityId(facility.getFacilityId())
                .ifPresent(existing -> {
                    throw new GeneralException(ErrorStatus.BUSINESS4003);
                });

        RequestedCondition condition = claim.getRequestedCondition();
        boolean wasEligible = facility.isEligibleForRecommendation();
        facility.confirmByOwner(
                condition.getPetAllowed(),
                condition.getMaxWeight(),
                condition.getMaxWeightInclusive(),
                condition.getRequirements(),
                condition.getConditionRaw()
        );
        if (wasEligible && !facility.isEligibleForRecommendation()) {
            eventPublisher.publishEvent(new FacilityBecameIneligibleEvent(facility.getFacilityId()));
        }

        claim.approve(adminUserId);
        log.info(
                "매장 등록 신청 승인: claimId={}, facilityId={}, adminUserId={}",
                claimId, facility.getFacilityId(), adminUserId
        );

        return BusinessConverter.toAdminClaimActionResult(claim);
    }

    /** 신청을 반려한다. 시설은 건드리지 않는다 — 대기 신청은 아직 시설에 아무 영향도 준 적이 없다. */
    public BusinessResponseDTO.AdminClaimActionResult reject(
            Long adminUserId,
            Long claimId,
            String reason
    ) {
        FacilityOwnerClaim claim = findClaimForUpdate(claimId);
        requireStatus(claim, ClaimStatus.PENDING);

        claim.reject(reason, adminUserId);
        log.info("매장 등록 신청 반려: claimId={}, adminUserId={}", claimId, adminUserId);

        return BusinessConverter.toAdminClaimActionResult(claim);
    }

    /**
     * 승인된 소유권을 해제한다(이의 제기 처리 등). 시설의 확정 상태도 함께 푼다 — 그러지 않으면 소유자가
     * 없는 매장이 계속 {@code CONFIRMED} 배지를 달고 있게 된다.
     */
    public BusinessResponseDTO.AdminClaimActionResult revoke(
            Long adminUserId,
            Long claimId,
            String reason
    ) {
        FacilityOwnerClaim claim = findClaimForUpdate(claimId);
        requireStatus(claim, ClaimStatus.APPROVED);

        Facility facility = facilityRepository.findByIdForUpdate(claim.getFacility().getFacilityId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));
        facility.releaseOwnerConfirmation();

        claim.revoke(reason, adminUserId);
        log.info(
                "매장 등록 승인 해제: claimId={}, facilityId={}, adminUserId={}",
                claimId, facility.getFacilityId(), adminUserId
        );

        return BusinessConverter.toAdminClaimActionResult(claim);
    }

    private FacilityOwnerClaim findClaimForUpdate(Long claimId) {
        return facilityOwnerClaimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BUSINESS4006));
    }

    private void requireStatus(
            FacilityOwnerClaim claim,
            ClaimStatus required
    ) {
        if (claim.getStatus() != required) {
            throw new GeneralException(ErrorStatus.BUSINESS4007);
        }
    }
}
