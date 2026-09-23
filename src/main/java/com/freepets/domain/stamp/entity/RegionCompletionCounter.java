package com.freepets.domain.stamp.entity;

import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시/군/구 하나당 행 1개 — "지금까지 이 지역을 완성한 사람이 몇 명인지"를 들고 있다가, 다음
 * 사람에게 줄 {@link RegionCompletion#getCompletionOrder()}를 원자적으로 내준다.
 *
 * <p>{@code RegionCompletionService}가 이 행을 비관적 락({@code PESSIMISTIC_WRITE})으로 잠그고
 * 증가시킨다 — 두 사용자가 같은(아직 아무도 완성하지 않은) 지역을 거의 동시에 처음 완성하면,
 * 잠금 없이는 둘 다 "1번째"를 받는 경합이 생긴다({@code GamificationService.grantXp}가 User
 * 행을 잠그는 것과 같은 이유).
 */
@Getter
@Entity
@Table(
        name = "region_completion_counters",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_region_completion_counters_region",
                columnNames = {"sido_code", "sigungu_code"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionCompletionCounter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_completion_counter_id")
    private Long regionCompletionCounterId;

    @Column(name = "sido_code", length = 10, nullable = false)
    private String sidoCode;

    @Column(name = "sigungu_code", length = 10, nullable = false)
    private String sigunguCode;

    @Column(name = "completed_count", nullable = false)
    private long completedCount;

    @Builder
    private RegionCompletionCounter(
            String sidoCode,
            String sigunguCode
    ) {
        this.sidoCode = sidoCode;
        this.sigunguCode = sigunguCode;
        this.completedCount = 0;
    }

    /**
     * 다음 순번을 내준다. 반환값이 곧 이번 완성자의 {@code completionOrder}다.
     */
    public long incrementAndGet() {
        this.completedCount++;
        return this.completedCount;
    }

}
