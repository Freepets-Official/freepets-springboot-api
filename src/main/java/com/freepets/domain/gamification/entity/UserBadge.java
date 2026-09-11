package com.freepets.domain.gamification.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 유저가 획득한 행동 기반 배지. (user, badge) 조합에 유니크 제약을 걸어 같은 배지를 두 번
// 받지 못하게 한다 — BadgeEvaluationService는 저장 전에 existsByUser_IdAndBadge로도 확인하지만,
// 동시 요청 경합까지 완전히 막으려면 DB 제약이 최종 방어선이다(Review의 부분 유니크 인덱스와 같은 결).
@Getter
@Entity
@Table(
        name = "user_badges",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_badges_user_badge", columnNames = {"user_id", "badge"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBadge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_badge_id")
    private Long userBadgeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Badge badge;

    @Builder
    private UserBadge(
            User user,
            Badge badge
    ) {
        this.user = user;
        this.badge = badge;
    }

}
