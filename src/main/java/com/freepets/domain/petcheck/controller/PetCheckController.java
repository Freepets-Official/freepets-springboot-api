package com.freepets.domain.petcheck.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.petcheck.dto.PetCheckRequestDTO;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.service.PetCheckCommandService;
import com.freepets.domain.petcheck.service.PetCheckQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PetCheckController {

    private final PetCheckCommandService petCheckCommandService;
    private final PetCheckQueryService petCheckQueryService;

    @PostMapping("/ai/check")
    public ApiResponse<PetCheckResponseDTO.CheckResult> createCheck(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PetCheckRequestDTO.CreateRequest request
    ) {
        return ApiResponse.onSuccess(
                petCheckCommandService.createCheck(userId, request)
        );
    }

    @GetMapping("/pet-checks")
    public ApiResponse<PetCheckResponseDTO.CheckHistoryList> getMyChecks(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long facilityId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return ApiResponse.onSuccess(
                petCheckQueryService.getMyChecks(userId, facilityId, limit, offset)
        );
    }
}
