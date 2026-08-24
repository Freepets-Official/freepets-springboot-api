package com.freepets.domain.review.entity;

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

}
