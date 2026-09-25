package com.freepets.domain.review.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.service.ReviewReportAdminCommandService;
import com.freepets.domain.review.service.ReviewReportAdminQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 리뷰 신고 관리자 처리. 일반 유저용 {@link ReviewController}와 호출 주체(관리자)·권한이 달라 컨트롤러를
 * 분리한다({@code BusinessAdminController}와 같은 방식).
 *
 * <p>경로가 {@code /api/v1/admin/**} 아래라 {@code SecurityConfig}의 관리자 규칙이 이미 적용된다 — 별도
 * 권한 검사를 여기서 하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class ReviewAdminController {

    private final ReviewReportAdminQueryService reviewReportAdminQueryService;
    private final ReviewReportAdminCommandService reviewReportAdminCommandService;

    @GetMapping("/reports")
    public ApiResponse<ReviewResponseDTO.AdminReportedReviewList> getReportedReviews(
            @RequestParam(name = "status", defaultValue = "PENDING") ReviewReportStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(
                reviewReportAdminQueryService.getReportedReviews(status, page, size)
        );
    }

    @PostMapping("/{reviewId}/reports/accept")
    public ApiResponse<ReviewResponseDTO.AdminReportActionResult> accept(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable("reviewId") Long reviewId
    ) {
        return ApiResponse.onSuccess(
                reviewReportAdminCommandService.accept(adminUserId, reviewId)
        );
    }

    @PostMapping("/{reviewId}/reports/reject")
    public ApiResponse<ReviewResponseDTO.AdminReportActionResult> reject(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable("reviewId") Long reviewId
    ) {
        return ApiResponse.onSuccess(
                reviewReportAdminCommandService.reject(adminUserId, reviewId)
        );
    }
}
