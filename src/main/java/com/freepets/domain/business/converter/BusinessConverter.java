package com.freepets.domain.business.converter;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.ConfidenceSource;
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

    /**
     * 신뢰도를 조회 시점에 계산하지 않고 여기서 고정해 내려준다. 방금 확정한 직후라 확정 이후에
     * 들어온 거부 제보가 있을 수 없고, 그래서 결과는 언제나 {@code CONFIRMED}/{@code OWNER}다.
     */
    public static BusinessResponseDTO.ClaimResult toClaimResult(Facility facility) {
        return new BusinessResponseDTO.ClaimResult(
                facility.getFacilityId(),
                Confidence.CONFIRMED,
                ConfidenceSource.OWNER,
                facility.getConfirmedAt()
        );
    }
}
