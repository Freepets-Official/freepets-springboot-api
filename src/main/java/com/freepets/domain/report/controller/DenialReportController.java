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

    @GetMapping("/facilities/{facilityId}/denial-reports/recent")
    public ApiResponse<List<DenialReportResponseDTO.Report>> getRecentDenialReports(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId
    ) {
        return ApiResponse.onSuccess(
                denialReportQueryService.getRecent(facilityId, userId)
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
