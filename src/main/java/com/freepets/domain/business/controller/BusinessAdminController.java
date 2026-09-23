package com.freepets.domain.business.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.service.FacilityOwnerClaimAdminCommandService;
import com.freepets.domain.business.service.FacilityOwnerClaimAdminQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 매장 등록 신청 관리자 심사. 자영업자용 {@link BusinessController}와 호출 주체(관리자)·권한이 달라 컨트롤러를
 * 분리한다(같은 도메인에 컨트롤러를 여러 개 두는 것은 {@code PetCheckController}/{@code PetCheckVerifyController}와
 * 같은 방식이다).
 *
 * <p>경로가 {@code /api/v1/admin/**} 아래라 {@code SecurityConfig}의 관리자 규칙이 이미 적용된다 — 별도
 * 권한 검사를 여기서 하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/business/claims")
@RequiredArgsConstructor
public class BusinessAdminController {

    private final FacilityOwnerClaimAdminQueryService facilityOwnerClaimAdminQueryService;
    private final FacilityOwnerClaimAdminCommandService facilityOwnerClaimAdminCommandService;

    @GetMapping
    public ApiResponse<BusinessResponseDTO.AdminClaimList> getClaims(
            @RequestParam(name = "status", defaultValue = "PENDING") ClaimStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(
                facilityOwnerClaimAdminQueryService.getClaims(status, page, size)
        );
    }

    @PostMapping("/{claimId}/approve")
    public ApiResponse<BusinessResponseDTO.AdminClaimActionResult> approve(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable("claimId") Long claimId
    ) {
        return ApiResponse.onSuccess(
                facilityOwnerClaimAdminCommandService.approve(adminUserId, claimId)
        );
    }

    @PostMapping("/{claimId}/reject")
    public ApiResponse<BusinessResponseDTO.AdminClaimActionResult> reject(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable("claimId") Long claimId,
            @Valid @RequestBody BusinessRequestDTO.RejectClaimRequest request
    ) {
        return ApiResponse.onSuccess(
                facilityOwnerClaimAdminCommandService.reject(adminUserId, claimId, request.getReason())
        );
    }

    @PostMapping("/{claimId}/revoke")
    public ApiResponse<BusinessResponseDTO.AdminClaimActionResult> revoke(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable("claimId") Long claimId,
            @Valid @RequestBody BusinessRequestDTO.RevokeClaimRequest request
    ) {
        return ApiResponse.onSuccess(
                facilityOwnerClaimAdminCommandService.revoke(adminUserId, claimId, request.getReason())
        );
    }
}
