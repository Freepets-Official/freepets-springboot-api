package com.freepets.domain.review.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportStatus;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    boolean existsByReviewReviewIdAndUserId(
            Long reviewId,
            Long userId
    );

    List<ReviewReport> findAllByStatusAndReviewFacilityFacilityId(
            ReviewReportStatus status,
            Long facilityId
    );

    List<ReviewReport> findAllByUserIdAndReviewReviewIdIn(
            Long userId,
            List<Long> reviewIds
    );
}
