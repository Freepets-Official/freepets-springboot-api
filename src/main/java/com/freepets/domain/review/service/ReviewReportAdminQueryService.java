package com.freepets.domain.review.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.review.converter.ReviewConverter;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.ReportedReviewSummary;
import com.freepets.domain.review.repository.ReviewReasonCount;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 신고 목록 조회. 일반 유저용 {@link ReviewQueryService}와는 호출 주체(관리자)와 권한이 달라
 * 클래스를 분리한다({@code FacilityOwnerClaimAdminQueryService}와 같은 방식).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewReportAdminQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;

    /**
     * 주어진 상태의 신고가 달린 리뷰 목록을 첫 신고가 오래된 순으로 내려준다. 리뷰 원문과 사유별 건수는
     * 페이지에 나온 리뷰들만 모아 한 번씩 조회한다 — 리뷰마다 따로 조회하면 페이지 크기만큼 쿼리가 늘어난다.
     */
    public ReviewResponseDTO.AdminReportedReviewList getReportedReviews(
            ReviewReportStatus status,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        // 0 이하로 오면 기본값으로, 상한을 넘으면 MAX_PAGE_SIZE로 잘라낸다.
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        Page<ReportedReviewSummary> summaryPage =
                reviewReportRepository.findReportedReviewSummaries(status, PageRequest.of(safePage, safeSize));

        List<Long> reviewIds = summaryPage.getContent().stream()
                .map(ReportedReviewSummary::reviewId)
                .toList();
        if (reviewIds.isEmpty()) {
            return ReviewConverter.toAdminReportedReviewList(summaryPage, Map.of(), Map.of());
        }

        Map<Long, Review> reviewById = reviewRepository.findAllWithFacilityAndUserByReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(Review::getReviewId, Function.identity()));

        Map<Long, Map<ReviewReportReason, Long>> reasonCountsByReviewId =
                reviewReportRepository.countReasonsByReviewIdIn(reviewIds, status).stream()
                        .collect(Collectors.groupingBy(
                                ReviewReasonCount::reviewId,
                                Collectors.toMap(
                                        ReviewReasonCount::reason,
                                        ReviewReasonCount::count,
                                        Long::sum,
                                        () -> new EnumMap<>(ReviewReportReason.class)
                                )
                        ));

        return ReviewConverter.toAdminReportedReviewList(summaryPage, reviewById, reasonCountsByReviewId);
    }
}
