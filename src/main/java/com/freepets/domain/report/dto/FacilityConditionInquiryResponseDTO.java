package com.freepets.domain.report.dto;

public class FacilityConditionInquiryResponseDTO {

    private FacilityConditionInquiryResponseDTO() {}

    // POST /facilities/{facilityId}/condition-inquiries 응답.
    public record InquireResult(
            Long inquiryId,
            Long facilityId
    ) {}

    // GET /facilities/{facilityId}/condition-inquiries/count 응답.
    public record CountResult(
            Long facilityId,
            long count
    ) {}
}
