package com.freepets.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.ReportedReviewSummary;
import com.freepets.domain.review.repository.ReviewReasonCount;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class ReviewReportAdminQueryServiceTest {

    private static final long REVIEW_ID = 31L;
    private static final LocalDateTime REPORTED_AT = LocalDateTime.of(2026, 9, 20, 10, 0);

    @Mock
    private ReviewReportRepository reviewReportRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewReportAdminQueryService reviewReportAdminQueryService;

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
        ReflectionTestUtils.setField(facility, "facilityId", 6L);

        User user = User.builder()
                .email("author@test.com")
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Review review = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(5)
                .ratingStaff(4)
                .ratingAmenity(3)
                .content("광고 글입니다")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.of(2026, 9, 1))
                .build();
        ReflectionTestUtils.setField(review, "reviewId", REVIEW_ID);
        return review;
    }

    @Test
    void getReportedReviews_리뷰_원문과_사유별_건수를_합쳐서_내려준다() {
        when(reviewReportRepository.findReportedReviewSummaries(eq(ReviewReportStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new ReportedReviewSummary(REVIEW_ID, 3, REPORTED_AT)),
                        PageRequest.of(0, 20),
                        1
                ));
        when(reviewRepository.findAllWithFacilityAndUserByReviewIdIn(List.of(REVIEW_ID)))
                .thenReturn(List.of(createReview()));
        when(reviewReportRepository.countReasonsByReviewIdIn(List.of(REVIEW_ID), ReviewReportStatus.PENDING))
                .thenReturn(List.of(
                        new ReviewReasonCount(REVIEW_ID, ReviewReportReason.SPAM, 2),
                        new ReviewReasonCount(REVIEW_ID, ReviewReportReason.ABUSE, 1)
                ));

        ReviewResponseDTO.AdminReportedReviewList result =
                reviewReportAdminQueryService.getReportedReviews(ReviewReportStatus.PENDING, 0, 20);

        assertThat(result.reviews()).hasSize(1);
        ReviewResponseDTO.AdminReportedReview reportedReview = result.reviews().get(0);
        assertThat(reportedReview.reviewId()).isEqualTo(REVIEW_ID);
        assertThat(reportedReview.facilityName()).isEqualTo("카페 파도살롱");
        assertThat(reportedReview.authorNickname()).isEqualTo("몽이아빠");
        assertThat(reportedReview.reportCount()).isEqualTo(3);
        assertThat(reportedReview.firstReportedAt()).isEqualTo(REPORTED_AT);
        assertThat(reportedReview.reasonCounts())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ReviewReportReason.SPAM, 2L,
                        ReviewReportReason.ABUSE, 1L
                ));
        assertThat(result.pageInfo().totalElements()).isEqualTo(1);
    }

    @Test
    void getReportedReviews_신고된_리뷰가_없으면_추가_조회_없이_빈_목록을_내려준다() {
        when(reviewReportRepository.findReportedReviewSummaries(eq(ReviewReportStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ReviewResponseDTO.AdminReportedReviewList result =
                reviewReportAdminQueryService.getReportedReviews(ReviewReportStatus.PENDING, 0, 20);

        assertThat(result.reviews()).isEmpty();
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void getReportedReviews_페이지_크기는_최대_50으로_제한한다() {
        when(reviewReportRepository.findReportedReviewSummaries(eq(ReviewReportStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        reviewReportAdminQueryService.getReportedReviews(ReviewReportStatus.PENDING, -1, 500);

        verify(reviewReportRepository).findReportedReviewSummaries(ReviewReportStatus.PENDING, PageRequest.of(0, 50));
    }
}
