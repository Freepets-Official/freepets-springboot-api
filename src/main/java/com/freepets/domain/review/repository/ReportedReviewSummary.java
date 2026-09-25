package com.freepets.domain.review.repository;

import java.time.LocalDateTime;

/**
 * 관리자 신고 목록의 리뷰 1건 요약. 신고를 리뷰 단위로 묶은 결과다
 * ({@link ReviewReportRepository#findReportedReviewSummaries} 참고).
 */
public record ReportedReviewSummary(
        Long reviewId,
        long reportCount,

        /** 이 리뷰에 들어온 신고 중 가장 먼저 접수된 시각. 목록은 이 값이 오래된 순이다. */
        LocalDateTime firstReportedAt
) {}
