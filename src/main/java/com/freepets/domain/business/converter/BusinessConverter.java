package com.freepets.domain.business.converter;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.entity.RequestedCondition;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.infra.nts.NtsValidationResult;

public class BusinessConverter {

    private BusinessConverter() {}

    public static BusinessResponseDTO.VerifyResult toVerifyResult(NtsValidationResult result) {
        return new BusinessResponseDTO.VerifyResult(
                result.valid(),
                result.statusCode(),
                result.statusLabel()
        );
    }

    public static BusinessResponseDTO.ClaimResult toClaimResult(FacilityOwnerClaim claim) {
        return new BusinessResponseDTO.ClaimResult(
                claim.getClaimId(),
                claim.getFacility().getFacilityId(),
                claim.getStatus()
        );
    }

    public static BusinessResponseDTO.MyClaimList toMyClaimList(List<FacilityOwnerClaim> claims) {
        return new BusinessResponseDTO.MyClaimList(
                claims.stream().map(BusinessConverter::toMyClaim).toList()
        );
    }

    private static BusinessResponseDTO.MyClaim toMyClaim(FacilityOwnerClaim claim) {
        Facility facility = claim.getFacility();
        return new BusinessResponseDTO.MyClaim(
                claim.getClaimId(),
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getReviewReason()
        );
    }

    public static BusinessResponseDTO.AdminClaimList toAdminClaimList(
            Page<FacilityOwnerClaim> claimPage,
            Set<Long> approvedFacilityIds
    ) {
        List<BusinessResponseDTO.AdminClaim> claims = claimPage.getContent().stream()
                .map(claim -> toAdminClaim(claim, approvedFacilityIds))
                .toList();

        return new BusinessResponseDTO.AdminClaimList(
                claims,
                new BusinessResponseDTO.PageInfo(
                        claimPage.getNumber(),
                        claimPage.getSize(),
                        claimPage.getTotalElements(),
                        claimPage.hasNext()
                )
        );
    }

    private static BusinessResponseDTO.AdminClaim toAdminClaim(
            FacilityOwnerClaim claim,
            Set<Long> approvedFacilityIds
    ) {
        Facility facility = claim.getFacility();
        // 4단계(신청 접수) 이전에 곧바로 승인 상태로 만들어진 기존 매장은 이 값을 저장한 적이 없어
        // null이다 — Hibernate는 @Embedded 컬럼이 전부 null이면 필드 자체를 null로 돌려준다.
        RequestedCondition condition = claim.getRequestedCondition();
        return new BusinessResponseDTO.AdminClaim(
                claim.getClaimId(),
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                claim.getUser().getId(),
                claim.getUser().getNickname(),
                claim.getMaskedBusinessNumber(),
                claim.getVerifiedAt(),
                condition == null ? null : condition.getPetAllowed(),
                condition == null ? null : condition.getMaxWeight(),
                condition == null ? null : condition.getMaxWeightInclusive(),
                condition == null ? List.of() : condition.getRequirements(),
                condition == null ? null : condition.getConditionRaw(),
                claim.getRegistrationCertificateUrl(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getReviewedAt(),
                claim.getReviewReason(),
                approvedFacilityIds.contains(facility.getFacilityId())
        );
    }

    public static BusinessResponseDTO.AdminClaimActionResult toAdminClaimActionResult(FacilityOwnerClaim claim) {
        return new BusinessResponseDTO.AdminClaimActionResult(
                claim.getClaimId(),
                claim.getFacility().getFacilityId(),
                claim.getStatus()
        );
    }
}
