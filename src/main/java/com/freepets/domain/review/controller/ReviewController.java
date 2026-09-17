package com.freepets.domain.review.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 게스트(토큰 없음)도 호출할 수 있다. 이때 {@code userId}는 null로 들어오며, 신고·좋아요한
     * 리뷰 표시 같은 개인화만 빠지고 나머지 리뷰 목록은 그대로 내려간다.
     */
    @GetMapping("/facilities/{facilityId}/reviews")
    public ApiResponse<ReviewResponseDTO.ReviewListResult> getReviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiResponse.onSuccess(
                reviewQueryService.getReviews(facilityId, userId, page, size)
        );
    }

    @PostMapping(value = "/facilities/{facilityId}/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewResponseDTO.UpsertResult> upsertReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId,
            @Valid @ModelAttribute ReviewRequestDTO.UpsertRequest request
    ) {
        return ApiResponse.onSuccess(
                reviewCommandService.upsertReview(userId, facilityId, request)
        );
    }

    @PutMapping(value = "/reviews/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewResponseDTO.UpsertResult> updateReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable("reviewId") Long reviewId,
            @Valid @ModelAttribute ReviewRequestDTO.UpsertRequest request
    ) {
        return ApiResponse.onSuccess(
                reviewCommandService.updateReview(userId, reviewId, request)
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

    @PostMapping("/reviews/{reviewId}/helpful")
    public ApiResponse<ReviewResponseDTO.HelpfulResult> markHelpful(
            @AuthenticationPrincipal Long userId,
            @PathVariable("reviewId") Long reviewId
    ) {
        return ApiResponse.onSuccess(
                reviewCommandService.markHelpful(userId, reviewId)
        );
    }

    @DeleteMapping("/reviews/{reviewId}/helpful")
    public ApiResponse<ReviewResponseDTO.HelpfulResult> unmarkHelpful(
            @AuthenticationPrincipal Long userId,
            @PathVariable("reviewId") Long reviewId
    ) {
        return ApiResponse.onSuccess(
                reviewCommandService.unmarkHelpful(userId, reviewId)
        );
    }
}
