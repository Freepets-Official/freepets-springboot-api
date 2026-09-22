package com.freepets.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.service.FacilityGradeCacheService;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class ReviewReportAdminCommandServiceTest {

    private static final long REVIEW_ID = 31L;
    private static final long FACILITY_ID = 6L;
    private static final long ADMIN_USER_ID = 99L;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReportRepository reviewReportRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityGradeCacheService facilityGradeCacheService;

    @InjectMocks
    private ReviewReportAdminCommandService reviewReportAdminCommandService;

    private Review createReview() {
        Facility facility = Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", FACILITY_ID);

        Review review = Review.builder()
                .facility(facility)
                .user(createUser("author@test.com"))
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("광고 글입니다")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.of(2026, 9, 1))
                .build();
        ReflectionTestUtils.setField(review, "reviewId", REVIEW_ID);
        return review;
    }

    private User createUser(String email) {
        return User.builder()
                .email(email)
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
    }

    private ReviewReport createPendingReport(
            Review review,
            ReviewReportReason reason
    ) {
        return ReviewReport.builder()
                .review(review)
                .user(createUser("reporter@test.com"))
                .reason(reason)
                .status(ReviewReportStatus.PENDING)
                .build();
    }

    // ---------------------------------------------------------------
    // accept
    // ---------------------------------------------------------------

    @Test
    void accept_대기_신고를_모두_승인하고_시설_등급을_다시_계산한다() {
        Review review = createReview();
        List<ReviewReport> pendingReports = List.of(
                createPendingReport(review, ReviewReportReason.SPAM),
                createPendingReport(review, ReviewReportReason.ABUSE)
        );
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewReportRepository.findAllByReviewIdAndStatusForUpdate(REVIEW_ID, ReviewReportStatus.PENDING))
                .thenReturn(pendingReports);

        ReviewResponseDTO.AdminReportActionResult result =
                reviewReportAdminCommandService.accept(ADMIN_USER_ID, REVIEW_ID);

        assertThat(result.status()).isEqualTo(ReviewReportStatus.ACCEPTED);
        assertThat(result.processedReportCount()).isEqualTo(2);
        assertThat(pendingReports)
                .allSatisfy(report -> {
                    assertThat(report.getStatus()).isEqualTo(ReviewReportStatus.ACCEPTED);
                    assertThat(report.getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
                    assertThat(report.getReviewedAt()).isNotNull();
                });
        // 리뷰 자체는 지우지 않는다 — 신고 상태만으로 목록·집계에서 빠진다.
        assertThat(review.isDeleted()).isFalse();
        verify(facilityGradeCacheService).refresh(FACILITY_ID);
    }

    // 같은 시설의 다른 리뷰를 동시에 승인해도 시설 캐시가 숨겨진 리뷰를 포함한 값으로 덮이지 않도록,
    // 신고 → 시설 순으로 잠근 뒤에 집계를 다시 계산해야 한다.
    @Test
    void accept_신고와_시설을_잠근_뒤에_시설_등급을_다시_계산한다() {
        Review review = createReview();
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewReportRepository.findAllByReviewIdAndStatusForUpdate(REVIEW_ID, ReviewReportStatus.PENDING))
                .thenReturn(List.of(createPendingReport(review, ReviewReportReason.SPAM)));

        reviewReportAdminCommandService.accept(ADMIN_USER_ID, REVIEW_ID);

        InOrder lockOrder = inOrder(reviewReportRepository, facilityRepository, facilityGradeCacheService);
        lockOrder.verify(reviewReportRepository).findAllByReviewIdAndStatusForUpdate(REVIEW_ID, ReviewReportStatus.PENDING);
        lockOrder.verify(facilityRepository).findByIdForUpdate(FACILITY_ID);
        lockOrder.verify(facilityGradeCacheService).refresh(FACILITY_ID);
    }

    @Test
    void accept_존재하지_않거나_삭제된_리뷰면_REVIEW4041() {
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(REVIEW_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> reviewReportAdminCommandService.accept(ADMIN_USER_ID, REVIEW_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4041);
        verifyNoInteractions(reviewReportRepository, facilityRepository, facilityGradeCacheService);
    }

    @Test
    void accept_대기_신고가_없으면_REVIEW4006이고_등급을_다시_계산하지_않는다() {
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(REVIEW_ID)).thenReturn(Optional.of(createReview()));
        when(reviewReportRepository.findAllByReviewIdAndStatusForUpdate(REVIEW_ID, ReviewReportStatus.PENDING))
                .thenReturn(List.of());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> reviewReportAdminCommandService.accept(ADMIN_USER_ID, REVIEW_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4006);
        verify(facilityGradeCacheService, never()).refresh(any());
    }

    // ---------------------------------------------------------------
    // reject
    // ---------------------------------------------------------------

    @Test
    void reject_대기_신고를_모두_반려하고_시설_등급은_건드리지_않는다() {
        Review review = createReview();
        List<ReviewReport> pendingReports = List.of(createPendingReport(review, ReviewReportReason.FALSE_INFO));
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reviewReportRepository.findAllByReviewIdAndStatusForUpdate(REVIEW_ID, ReviewReportStatus.PENDING))
                .thenReturn(pendingReports);

        ReviewResponseDTO.AdminReportActionResult result =
                reviewReportAdminCommandService.reject(ADMIN_USER_ID, REVIEW_ID);

        assertThat(result.status()).isEqualTo(ReviewReportStatus.REJECTED);
        assertThat(result.processedReportCount()).isEqualTo(1);
        assertThat(pendingReports.get(0).getStatus()).isEqualTo(ReviewReportStatus.REJECTED);
        assertThat(pendingReports.get(0).getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
        verifyNoInteractions(facilityRepository, facilityGradeCacheService);
    }

    @Test
    void reject_대기_신고가_없으면_REVIEW4006() {
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(REVIEW_ID)).thenReturn(Optional.of(createReview()));
        when(reviewReportRepository.findAllByReviewIdAndStatusForUpdate(REVIEW_ID, ReviewReportStatus.PENDING))
                .thenReturn(List.of());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> reviewReportAdminCommandService.reject(ADMIN_USER_ID, REVIEW_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4006);
    }
}
