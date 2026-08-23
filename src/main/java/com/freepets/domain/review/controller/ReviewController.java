package com.freepets.domain.review.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.review.dto.ReviewRequestDTO;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.service.ReviewCommandService;
import com.freepets.domain.review.service.ReviewQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewCommandService reviewCommandService;
    private final ReviewQueryService reviewQueryService;

    @GetMapping("/facilities/{facilityId}/reviews")
    public ApiResponse<ReviewResponseDTO.ReviewListResult> getReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId
    ) {
        return ApiResponse.onSuccess(
                reviewQueryService.getReviews(facilityId, userId)
        );
    }

    @PostMapping("/facilities/{facilityId}/reviews")
    public ApiResponse<ReviewResponseDTO.UpsertResult> upsertReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId,
            @Valid @RequestBody ReviewRequestDTO.UpsertRequest request
    ) {
        return ApiResponse.onSuccess(
                reviewCommandService.upsertReview(userId, facilityId, request)
        );
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<ReviewResponseDTO.DeleteResult> deleteReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable("reviewId") Long reviewId
    ) {
        return ApiResponse.onSuccess(
                reviewCommandService.deleteReview(userId, reviewId)
        );
    }

    @PostMapping("/reviews/{reviewId}/report")
    public ApiResponse<ReviewResponseDTO.ReportResult> reportReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable("reviewId") Long reviewId,
            @Valid @RequestBody ReviewRequestDTO.ReportRequest request
    ) {
        return ApiResponse.onSuccess(
                reviewCommandService.reportReview(userId, reviewId, request)
        );
    }
}
