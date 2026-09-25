package com.freepets.domain.review.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.service.FacilityGradeCacheService;
import com.freepets.domain.review.converter.ReviewConverter;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자의 리뷰 신고 승인·반려. 신고는 리뷰 단위로 처리한다 — 한 리뷰의 대기 신고 전체가 한꺼번에
 * 같은 상태로 바뀐다. 일반 유저용 {@link ReviewCommandService}와는 호출 주체(관리자)와 권한이 달라
 * 클래스를 분리한다({@code FacilityOwnerClaimAdminCommandService}와 같은 방식).
 *
 * <p><b>락 순서</b>: 신고 행을 먼저 잠그고, 승인은 그다음 시설 행을 잠근다
 * ({@code FacilityOwnerClaimAdminCommandService}와 같은 순서 — 순서가 엇갈리면 교착 상태가 생길 수 있다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewReportAdminCommandService {

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityGradeCacheService facilityGradeCacheService;

    /**
     * 대기 신고를 모두 승인한다. 리뷰 자체는 지우지 않는다 — 승인된 신고가 달린 리뷰는 목록·등급 집계가
     * 이미 제외하므로({@code ReviewRepository.aggregateByFacilityId} 참고) 신고 상태만 바꾸면 숨겨진다.
     *
     * <p>집계에서 리뷰가 빠지므로 시설에 저장해둔 점수·리뷰 수·등급을 다시 계산한다. 그 전에 시설 행을
     * 잠근다 — 같은 시설의 다른 리뷰를 동시에 승인하면 두 트랜잭션이 서로의 승인이 커밋되기 전 집계를
     * 읽고, 나중에 커밋한 쪽이 이미 숨겨진 리뷰가 포함된 값을 시설에 덮어쓴다. 잠그면 나중 쪽은 앞선
     * 승인이 커밋된 뒤에 집계를 읽는다.
     */
    public ReviewResponseDTO.AdminReportActionResult accept(
            Long adminUserId,
            Long reviewId
    ) {
        Review review = findReview(reviewId);
        List<ReviewReport> pendingReports = findPendingReportsForUpdate(reviewId);

        pendingReports.forEach(report -> report.accept(adminUserId));

        Long facilityId = review.getFacility().getFacilityId();
        facilityRepository.findByIdForUpdate(facilityId);
        facilityGradeCacheService.refresh(facilityId);

        log.info(
                "리뷰 신고 승인: reviewId={}, reportCount={}, adminUserId={}",
                reviewId, pendingReports.size(), adminUserId
        );

        return ReviewConverter.toAdminReportActionResult(reviewId, ReviewReportStatus.ACCEPTED, pendingReports.size());
    }

    /** 대기 신고를 모두 반려한다. 집계는 승인된 신고만 보므로 등급을 다시 계산하지 않는다. */
    public ReviewResponseDTO.AdminReportActionResult reject(
            Long adminUserId,
            Long reviewId
    ) {
        findReview(reviewId);
        List<ReviewReport> pendingReports = findPendingReportsForUpdate(reviewId);

        pendingReports.forEach(report -> report.reject(adminUserId));

        log.info(
                "리뷰 신고 반려: reviewId={}, reportCount={}, adminUserId={}",
                reviewId, pendingReports.size(), adminUserId
        );

        return ReviewConverter.toAdminReportActionResult(reviewId, ReviewReportStatus.REJECTED, pendingReports.size());
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW4041));
    }

    private List<ReviewReport> findPendingReportsForUpdate(Long reviewId) {
        List<ReviewReport> pendingReports =
                reviewReportRepository.findAllByReviewIdAndStatusForUpdate(reviewId, ReviewReportStatus.PENDING);
        if (pendingReports.isEmpty()) {
            throw new GeneralException(ErrorStatus.REVIEW4006);
        }
        return pendingReports;
    }
}
