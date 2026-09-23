package com.freepets.domain.review.entity;

import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 리뷰 하나에 유저 한 명이 남기는 "도움됐어요" 표시. 유니크 제약(review_id, user_id)으로
// 같은 사람이 두 번 표시해 Review.helpfulCount가 중복 집계되는 걸 DB 단에서도 막는다 —
// XpEvent.uk_xp_events_user_source와 같은 이유로, 서비스 계층의 exists 확인만으로는
// 거의 동시에 두 번 눌린 요청까지는 못 막는다.
@Getter
@Entity
@Table(
        name = "review_helpfuls",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_helpfuls_review_user",
                columnNames = {"review_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewHelpful extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_helpful_id")
    private Long reviewHelpfulId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    private ReviewHelpful(
            Review review,
            User user
    ) {
        this.review = review;
        this.user = user;
    }

}
