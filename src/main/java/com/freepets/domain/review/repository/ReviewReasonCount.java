package com.freepets.domain.review.repository;

import com.freepets.domain.review.entity.ReviewReportReason;

/**
 * 리뷰 1건에 들어온 신고의 사유별 건수({@link ReviewReportRepository#countReasonsByReviewIdIn} 참고).
 */
public record ReviewReasonCount(
        Long reviewId,
        ReviewReportReason reason,
        long count
) {}
