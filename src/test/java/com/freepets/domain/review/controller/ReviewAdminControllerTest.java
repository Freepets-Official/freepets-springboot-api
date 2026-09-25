package com.freepets.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.service.ReviewReportAdminCommandService;
import com.freepets.domain.review.service.ReviewReportAdminQueryService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

/**
 * 권한 검사(관리자만 접근 가능)는 여기서 다시 확인하지 않는다 — {@code addFilters = false}라 필터가
 * 아예 안 돌고, 그 검사는 {@code SecurityFilterChainTest}가 맡고 있다.
 */
@WebMvcTest(ReviewAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewAdminControllerTest {

    private static final LocalDateTime REPORTED_AT = LocalDateTime.of(2026, 9, 20, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewReportAdminQueryService reviewReportAdminQueryService;

    @MockitoBean
    private ReviewReportAdminCommandService reviewReportAdminCommandService;

    private ReviewResponseDTO.AdminReportedReview reportedReview() {
        return new ReviewResponseDTO.AdminReportedReview(
                31L, 6L, "카페 파도살롱",
                1L, "몽이아빠", "광고 글입니다", null,
                5, 5, 5, REPORTED_AT.minusDays(3),
                3, Map.of(ReviewReportReason.SPAM, 2L, ReviewReportReason.ABUSE, 1L),
                REPORTED_AT
        );
    }

    @Test
    void getReportedReviews_성공하면_200과_목록을_반환한다() throws Exception {
        when(reviewReportAdminQueryService.getReportedReviews(eq(ReviewReportStatus.PENDING), anyInt(), anyInt()))
                .thenReturn(new ReviewResponseDTO.AdminReportedReviewList(
                        List.of(reportedReview()),
                        new ReviewResponseDTO.PageInfo(0, 20, 1, false)
                ));

        mockMvc.perform(get("/api/v1/admin/reviews/reports").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.reviews[0].reviewId").value(31))
                .andExpect(jsonPath("$.result.reviews[0].reportCount").value(3))
                .andExpect(jsonPath("$.result.reviews[0].reasonCounts.SPAM").value(2))
                .andExpect(jsonPath("$.result.pageInfo.totalElements").value(1));
    }

    @Test
    void getReportedReviews_상태_기본값은_PENDING이다() throws Exception {
        when(reviewReportAdminQueryService.getReportedReviews(eq(ReviewReportStatus.PENDING), anyInt(), anyInt()))
                .thenReturn(new ReviewResponseDTO.AdminReportedReviewList(
                        List.of(),
                        new ReviewResponseDTO.PageInfo(0, 20, 0, false)
                ));

        mockMvc.perform(get("/api/v1/admin/reviews/reports"))
                .andExpect(status().isOk());
    }

    @Test
    void getReportedReviews_잘못된_상태_값이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews/reports").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(reviewReportAdminQueryService);
    }

    @Test
    void accept_성공하면_200과_승인_결과를_반환한다() throws Exception {
        when(reviewReportAdminCommandService.accept(any(), eq(31L)))
                .thenReturn(new ReviewResponseDTO.AdminReportActionResult(31L, ReviewReportStatus.ACCEPTED, 3));

        mockMvc.perform(post("/api/v1/admin/reviews/31/reports/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.result.processedReportCount").value(3));
    }

    @Test
    void accept_존재하지_않는_리뷰면_404를_반환한다() throws Exception {
        when(reviewReportAdminCommandService.accept(any(), eq(31L)))
                .thenThrow(new GeneralException(ErrorStatus.REVIEW4041));

        mockMvc.perform(post("/api/v1/admin/reviews/31/reports/accept"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW4041"));
    }

    @Test
    void accept_대기_신고가_없으면_409를_반환한다() throws Exception {
        when(reviewReportAdminCommandService.accept(any(), eq(31L)))
                .thenThrow(new GeneralException(ErrorStatus.REVIEW4006));

        mockMvc.perform(post("/api/v1/admin/reviews/31/reports/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW4006"));
    }

    @Test
    void reject_성공하면_200과_반려_결과를_반환한다() throws Exception {
        when(reviewReportAdminCommandService.reject(any(), eq(31L)))
                .thenReturn(new ReviewResponseDTO.AdminReportActionResult(31L, ReviewReportStatus.REJECTED, 3));

        mockMvc.perform(post("/api/v1/admin/reviews/31/reports/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"));
    }
}
