package com.freepets.domain.stamp.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.stamp.dto.StampRequestDTO;
import com.freepets.domain.stamp.dto.StampResponseDTO;
import com.freepets.domain.stamp.service.StampCommandService;
import com.freepets.domain.stamp.service.StampQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/stamps")
@RequiredArgsConstructor
public class StampController {

    private final StampCommandService stampCommandService;
    private final StampQueryService stampQueryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StampResponseDTO.StampResult> createStamp(
            @AuthenticationPrincipal Long userId,
            @Valid @ModelAttribute StampRequestDTO.CreateRequest request
    ) {
        return ApiResponse.onSuccess(
                stampCommandService.createStamp(userId, request)
        );
    }

    @GetMapping
    public ApiResponse<StampResponseDTO.MyStamps> getMyStamps(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                stampQueryService.getMyStamps(userId)
        );
    }

}
