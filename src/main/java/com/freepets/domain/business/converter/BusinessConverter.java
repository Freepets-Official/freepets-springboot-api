package com.freepets.domain.business.converter;

import java.util.List;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
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
                claim.getCreatedAt()
        );
    }
}
