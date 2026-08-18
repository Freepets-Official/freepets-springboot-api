package com.freepets.domain.pet.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.pet.dto.PetRequestDTO;
import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.pet.service.PetCommandService;
import com.freepets.domain.pet.service.PetQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetCommandService petCommandService;
    private final PetQueryService petQueryService;

    @PostMapping
    public ApiResponse<PetResponseDTO.CreateResult> createPet(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PetRequestDTO.CreateRequest request
    ) {
        return ApiResponse.onSuccess(
                petCommandService.createPet(userId, request)
        );
    }

    @GetMapping
    public ApiResponse<PetResponseDTO.PetList> getMyPets(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                petQueryService.getMyPets(userId)
        );
    }

    @GetMapping("/{petId}")
    public ApiResponse<PetResponseDTO.PetDetail> getPet(
            @AuthenticationPrincipal Long userId,
            @PathVariable("petId") Long petId
    ) {
        return ApiResponse.onSuccess(
                petQueryService.getPet(userId, petId)
        );
    }

    @PutMapping("/{petId}")
    public ApiResponse<PetResponseDTO.PetDetail> updatePet(
            @AuthenticationPrincipal Long userId,
            @PathVariable("petId") Long petId,
            @Valid @RequestBody PetRequestDTO.UpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                petCommandService.updatePet(userId, petId, request)
        );
    }

    @DeleteMapping("/{petId}")
    public ApiResponse<PetResponseDTO.DeleteResult> deletePet(
            @AuthenticationPrincipal Long userId,
            @PathVariable("petId") Long petId
    ) {
        return ApiResponse.onSuccess(
                petCommandService.deletePet(userId, petId)
        );
    }
}
