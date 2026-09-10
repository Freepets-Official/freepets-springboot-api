package com.freepets.domain.gamification.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.gamification.dto.GamificationRequestDTO;
import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.service.GamificationQueryService;
import com.freepets.domain.gamification.service.GamificationService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/gamification")
@RequiredArgsConstructor
public class GamificationController {

    private final GamificationQueryService gamificationQueryService;
    private final GamificationService gamificationService;

    @GetMapping
    public ApiResponse<GamificationResponseDTO.MyStatus> getMyStatus(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                gamificationQueryService.getMyStatus(userId)
        );
    }

    @PatchMapping("/notification")
    public ApiResponse<GamificationResponseDTO.NotificationResult> updateNotification(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GamificationRequestDTO.NotificationRequest request
    ) {
        boolean enabled = gamificationService.updateLevelUpNotification(userId, request.getLevelUpNotificationEnabled());
        return ApiResponse.onSuccess(
                new GamificationResponseDTO.NotificationResult(enabled)
        );
    }

}
