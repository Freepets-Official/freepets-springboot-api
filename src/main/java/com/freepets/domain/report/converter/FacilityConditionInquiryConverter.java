package com.freepets.domain.report.converter;

import com.freepets.domain.report.dto.FacilityConditionInquiryResponseDTO;
import com.freepets.domain.report.entity.FacilityConditionInquiry;

public class FacilityConditionInquiryConverter {

    private FacilityConditionInquiryConverter() {}

    public static FacilityConditionInquiryResponseDTO.InquireResult toInquireResult(FacilityConditionInquiry inquiry) {
        return new FacilityConditionInquiryResponseDTO.InquireResult(
                inquiry.getInquiryId(),
                inquiry.getFacility().getFacilityId()
        );
    }
}
