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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 경험치 지급 이력(append-only 원장). 감사 추적뿐 아니라 두 가지 규칙을 여기서 직접 센다 —
// (1) 도메인별 하루 지급 상한, (2) sourceId가 있는 지급의 평생 1회 여부(코스 공개 등). 두 규칙
// 모두 GamificationService가 이 테이블을 조회해서 판단하므로, 행 자체엔 그 이상의 상태를 담지
// 않는다(수정될 일이 없는 로그라 BaseEntity의 updatedAt은 쓰이지 않지만, 이 리포 대부분의
// 엔티티가 BaseEntity를 상속하는 관례를 따른다).
@Getter
@Entity
@Table(
        name = "xp_events",
        indexes = {
                // 하루 상한 조회(user_id + source_type + createdAt 범위)가 이 인덱스를 탄다.
                @Index(name = "idx_xp_events_user_source_created", columnList = "user_id, source_type, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class XpEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "xp_event_id")
    private Long xpEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private XpSourceType sourceType;

    // 이 지급을 유발한 행의 id(리뷰 id, 코스 id 등). 평생 1회 지급 판단에만 쓰고, 하루 상한처럼
    // 여러 번 지급 가능한 유형(판별 등)은 null로 둔다.
    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false)
    private int amount;

    @Builder
    private XpEvent(
            User user,
            XpSourceType sourceType,
            Long sourceId,
            int amount
    ) {
        this.user = user;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.amount = amount;
    }

}
