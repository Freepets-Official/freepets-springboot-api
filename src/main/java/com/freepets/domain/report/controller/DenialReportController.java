package com.freepets.domain.report.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.report.dto.DenialReportRequestDTO;
import com.freepets.domain.report.dto.DenialReportResponseDTO;
import com.freepets.domain.report.service.DenialReportCommandService;
import com.freepets.domain.report.service.DenialReportQueryService;
import com.freepets.global.apiPayload.ApiResponse;
import com.freepets.global.security.CurrentUserResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DenialReportController {

    private final DenialReportCommandService denialReportCommandService;
    private final DenialReportQueryService denialReportQueryService;

    @PostMapping("/facilities/{facilityId}/denial-reports")
    public ApiResponse<DenialReportResponseDTO.Report> reportDenial(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId,
            @Valid @RequestBody DenialReportRequestDTO.CreateRequest request
    ) {
        return ApiResponse.onSuccess(
                denialReportCommandService.report(userId, facilityId, request.getReason())
        );
    }

    // 로그인 불필요 — 게스트 모드로도 최근 거부 제보를 볼 수 있어야 한다. 로그인 상태면 본인이
    // 남긴 제보는 제외하고, 아니면(CurrentUserResolver가 null 반환) 전부 포함한다
    // (DenialReportQueryService.getRecent 참고 — permitAll 경로라 @AuthenticationPrincipal을
    // 그대로 못 쓴다).
    @GetMapping("/facilities/{facilityId}/denial-reports/recent")
    public ApiResponse<List<DenialReportResponseDTO.Report>> getRecentDenialReports(
            @PathVariable Long facilityId
    ) {
        return ApiResponse.onSuccess(
                denialReportQueryService.getRecent(facilityId, CurrentUserResolver.resolveOptionalUserId())
        );
    }

    @GetMapping("/facilities/{facilityId}/denial-reports/mine")
    public ApiResponse<DenialReportResponseDTO.Report> getMyDenialReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId
    ) {
        return ApiResponse.onSuccess(
                denialReportQueryService.getMine(facilityId, userId)
        );
    }

    @GetMapping("/me/denial-alerts")
    public ApiResponse<List<DenialReportResponseDTO.DenialAlert>> getMyDenialAlerts(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                denialReportQueryService.getMyDenialAlerts(userId)
        );
    }
}
