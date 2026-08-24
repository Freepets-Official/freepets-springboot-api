package com.freepets.domain.petsatisfaction.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.petsatisfaction.dto.PetSatisfactionRequestDTO;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionResponseDTO;
import com.freepets.domain.petsatisfaction.service.PetSatisfactionCommandService;
import com.freepets.domain.petsatisfaction.service.PetSatisfactionQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PetSatisfactionController {

    private final PetSatisfactionCommandService petSatisfactionCommandService;
    private final PetSatisfactionQueryService petSatisfactionQueryService;

    @PostMapping("/facilities/{facilityId}/pets/{petId}/satisfaction")
    public ApiResponse<PetSatisfactionResponseDTO.UpsertResult> upsertSatisfaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId,
            @PathVariable("petId") Long petId,
            @Valid @RequestBody PetSatisfactionRequestDTO.UpsertRequest request
    ) {
        return ApiResponse.onSuccess(
                petSatisfactionCommandService.upsertSatisfaction(userId, facilityId, petId, request)
        );
    }

    @GetMapping("/facilities/{facilityId}/pets/satisfactions")
    public ApiResponse<PetSatisfactionResponseDTO.FacilitySatisfactionList> getFacilitySatisfactions(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId
    ) {
        return ApiResponse.onSuccess(
                petSatisfactionQueryService.getFacilitySatisfactions(userId, facilityId)
        );
    }
}
