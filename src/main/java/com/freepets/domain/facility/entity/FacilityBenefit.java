package com.freepets.domain.facility.entity;

import org.hibernate.annotations.ColumnDefault;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사장님이 등록하는 방문 혜택 한 건(예: 출입증 제시 시 음료 10% 할인). 시설당 여러 건이 있을 수 있고,
 * {@code Facility}가 항상 전량을 함께 들고 있을 필요는 없어(거부 제보·리뷰와 같은 이유) {@code Facility}에
 * 컬렉션을 두지 않고 이 엔티티의 전용 리포지토리로 조회한다.
 *
 * <p>{@code isEnabled}를 끄면 손님에게는 숨겨지지만 행은 지워지지 않는다 — 계절 혜택을 지웠다 다시
 * 쓰지 않아도 된다. 실제 삭제(하드 삭제)는 사장님이 목록에서 직접 삭제할 때만 일어나는 별개 동작이다.
 */
@Getter
@Entity
@Table(name = "facility_benefits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityBenefit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facility_benefit_id")
    private Long facilityBenefitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(length = 300)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    @ColumnDefault("true")
    private boolean isEnabled;

    @Builder
    private FacilityBenefit(
            Facility facility,
            String title,
            String description
    ) {
        this.facility = facility;
        this.title = title;
        this.description = description;
        this.isEnabled = true;
    }

    public void enable() {
        this.isEnabled = true;
    }

    public void disable() {
        this.isEnabled = false;
    }
}
