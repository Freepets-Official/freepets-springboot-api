package com.freepets.domain.facility.entity;

import java.time.LocalDate;

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
 * 시설의 발자국 등급을 매일 한 번 찍어두는 스냅샷 — 사업자 대시보드의 "등급 추이" 그래프가 읽는다.
 *
 * <p>{@code Facility.pawGradeLevel}/{@code petScore}는 현재값만 들고 있어 추이를 그릴 수 없다.
 * {@code FacilityGradeSnapshotScheduler}가 매일 그 시점의 값을 그대로 옮겨 적재한다.
 *
 * <p>{@code (facility, snapshotDate)} 조합은 하루 1건이다 — 스케줄러가 같은 날 다시 돌아도
 * 저장 전에 존재 여부를 확인해 중복을 막는다({@code FacilityGradeSnapshotService} 참고).
 */
@Getter
@Entity
@Table(
        name = "facility_grade_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_facility_grade_snapshots_facility_date",
                columnNames = {"facility_id", "snapshot_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityGradeSnapshot extends BaseEntity {

    /**
     * 등급 추이 화면이 보장하는 조회 기간. {@code OwnerFacilityQueryService}의 조회 범위와
     * {@code FacilityGradeSnapshotService}의 보관 기간이 이 값 하나를 함께 참조한다 — 따로 들고
     * 있으면 한쪽만 바뀌었을 때 화면이 조용히 잘릴 수 있다.
     */
    public static final long TREND_WINDOW_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facility_grade_snapshot_id")
    private Long facilityGradeSnapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "paw_grade_level", nullable = false)
    private int pawGradeLevel;

    @Column(name = "pet_score")
    private Double petScore;

    @Column(name = "review_count", nullable = false)
    private long reviewCount;

    @Builder
    private FacilityGradeSnapshot(
            Facility facility,
            LocalDate snapshotDate,
            int pawGradeLevel,
            Double petScore,
            long reviewCount
    ) {
        this.facility = facility;
        this.snapshotDate = snapshotDate;
        this.pawGradeLevel = pawGradeLevel;
        this.petScore = petScore;
        this.reviewCount = reviewCount;
    }
}
