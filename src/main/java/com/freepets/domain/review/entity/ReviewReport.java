package com.freepets.domain.review.entity;

import java.time.LocalDateTime;

import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewReportStatus status;

    // 운영자가 승인·반려한 시각과 운영자 userId. 아직 대기 중이면 둘 다 null이다
    // (FacilityOwnerClaim.reviewedAt/reviewedByUserId와 같은 의미).
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Builder
    private ReviewReport(
            Review review,
            User user,
            ReviewReportReason reason,
            ReviewReportStatus status
    ) {
        this.review = review;
        this.user = user;
        this.reason = reason;
        this.status = status;
    }

    // 승인된 신고가 1건이라도 달린 리뷰는 목록·등급 집계에서 빠진다(ReviewRepository.aggregateByFacilityId 참고).
    public void accept(Long adminUserId) {
        this.status = ReviewReportStatus.ACCEPTED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedByUserId = adminUserId;
    }

    public void reject(Long adminUserId) {
        this.status = ReviewReportStatus.REJECTED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedByUserId = adminUserId;
    }

}
