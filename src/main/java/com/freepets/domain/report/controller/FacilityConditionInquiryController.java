package com.freepets.domain.report.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.report.dto.FacilityConditionInquiryRequestDTO;
import com.freepets.domain.report.dto.FacilityConditionInquiryResponseDTO;
import com.freepets.domain.report.service.FacilityConditionInquiryCommandService;
import com.freepets.domain.report.service.FacilityConditionInquiryQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/facilities/{facilityId}/condition-inquiries")
@RequiredArgsConstructor
public class FacilityConditionInquiryController {

    private final FacilityConditionInquiryCommandService facilityConditionInquiryCommandService;
    private final FacilityConditionInquiryQueryService facilityConditionInquiryQueryService;

    // 본문 없이(시설 id + 인증 토큰만) 호출해도 되고, 선택 메모를 같이 보낼 수도 있다 —
    // required = false라 본문 자체를 안 보내면 request가 null로 들어온다.
    @PostMapping
    public ApiResponse<FacilityConditionInquiryResponseDTO.InquireResult> inquire(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId,
            @Valid @RequestBody(required = false) FacilityConditionInquiryRequestDTO.InquireRequest request
    ) {
        String memo = request == null ? null : request.getMemo();
        return ApiResponse.onSuccess(
                facilityConditionInquiryCommandService.inquire(userId, facilityId, memo)
        );
    }

    @GetMapping("/count")
    public ApiResponse<FacilityConditionInquiryResponseDTO.CountResult> getCount(
            @PathVariable Long facilityId
    ) {
        return ApiResponse.onSuccess(
                facilityConditionInquiryQueryService.getCount(facilityId)
        );
    }

}
