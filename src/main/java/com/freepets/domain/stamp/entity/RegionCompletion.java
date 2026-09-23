package com.freepets.domain.stamp.entity;

import java.time.LocalDateTime;

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

/**
 * 사용자가 특정 시/군/구에서 처음 도장을 찍은 순간을 "그 지역을 완성했다"고 보고 남기는 기록
 * — "○○시 n번째 등반자"({@code freepets-docs/docs/13-지역-랭킹.md} 2절) 연출에 쓴다. 한 번
 * 정해지면 바뀌지 않아야 해서(다른 사용자가 늘어나도 내 순번이 바뀌면 안 됨) 조회할 때마다
 * 다시 세지 않고 완성 시점에 {@link RegionCompletionCounter}로 원자적으로 확정해 저장한다.
 */
@Getter
@Entity
@Table(
        name = "region_completions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_region_completions_user_region",
                columnNames = {"user_id", "sido_code", "sigungu_code"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionCompletion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_completion_id")
    private Long regionCompletionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sido_code", length = 10, nullable = false)
    private String sidoCode;

    @Column(name = "sigungu_code", length = 10, nullable = false)
    private String sigunguCode;

    @Column(name = "completion_order", nullable = false)
    private long completionOrder;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Builder
    private RegionCompletion(
            User user,
            String sidoCode,
            String sigunguCode,
            long completionOrder,
            LocalDateTime completedAt
    ) {
        this.user = user;
        this.sidoCode = sidoCode;
        this.sigunguCode = sigunguCode;
        this.completionOrder = completionOrder;
        this.completedAt = completedAt;
    }

}
