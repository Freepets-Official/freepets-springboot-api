package com.freepets.domain.business.converter;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
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
}
