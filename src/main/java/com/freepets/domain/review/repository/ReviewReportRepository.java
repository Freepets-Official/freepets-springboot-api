package com.freepets.domain.review.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportStatus;

import jakarta.persistence.LockModeType;

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

    /**
     * 관리자 신고 목록 — 신고를 리뷰 단위로 묶는다. 운영자는 신고 1건이 아니라 리뷰 1건을 보고
     * 숨길지 판단하기 때문이다. 오래 방치된 신고부터 처리하도록 첫 신고 시각의 오름차순이다.
     *
     * <p>작성자가 이미 지운 리뷰는 뺀다 — 목록·집계에 더는 나오지 않아 처리할 의미가 없다.
     *
     * <p>{@code group by} 쿼리라 Spring Data가 count 쿼리를 만들어주지 못해 직접 적는다. 리뷰 수를
     * 세야 하므로 {@code count(distinct ...)}다.
     */
    @Query(value = """
            SELECT new com.freepets.domain.review.repository.ReportedReviewSummary(
                report.review.reviewId, COUNT(report), MIN(report.createdAt))
            FROM ReviewReport report
            WHERE report.status = :status
            AND report.review.deletedAt IS NULL
            GROUP BY report.review.reviewId
            ORDER BY MIN(report.createdAt) ASC, report.review.reviewId ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT report.review.reviewId)
            FROM ReviewReport report
            WHERE report.status = :status
            AND report.review.deletedAt IS NULL
            """)
    Page<ReportedReviewSummary> findReportedReviewSummaries(
            @Param("status") ReviewReportStatus status,
            Pageable pageable
    );

    /** 목록 한 페이지에 나온 리뷰들의 사유별 신고 건수. 리뷰마다 따로 세지 않도록 한 번에 묶는다. */
    @Query("""
            SELECT new com.freepets.domain.review.repository.ReviewReasonCount(
                report.review.reviewId, report.reason, COUNT(report))
            FROM ReviewReport report
            WHERE report.review.reviewId IN :reviewIds
            AND report.status = :status
            GROUP BY report.review.reviewId, report.reason
            """)
    List<ReviewReasonCount> countReasonsByReviewIdIn(
            @Param("reviewIds") Collection<Long> reviewIds,
            @Param("status") ReviewReportStatus status
    );

    /**
     * 신고 처리용 — 관리자 두 명이 같은 리뷰를 동시에 승인·반려하면 한쪽의 결과가 다른 쪽을 덮어쓴다.
     * 행을 잠가서 나중에 온 쪽은 앞선 처리가 끝난 뒤에 조회하게 한다(그러면 대기 신고가 없어 막힌다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT report FROM ReviewReport report
            WHERE report.review.reviewId = :reviewId
            AND report.status = :status
            """)
    List<ReviewReport> findAllByReviewIdAndStatusForUpdate(
            @Param("reviewId") Long reviewId,
            @Param("status") ReviewReportStatus status
    );
}
