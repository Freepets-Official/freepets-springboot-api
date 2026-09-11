package com.freepets.domain.report.entity;

import com.freepets.domain.facility.entity.Facility;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자가 "이 시설 반려동물 동반 조건이 불명확하다"고 보내는 요청. FacilityReport(제보)와 달리
// 검토·승인 대상이 아니라 그냥 쌓아두는 신호다 — 누구를 문책하는 게 아니라 "여기 조건 좀 알려
// 주세요"라는 단순 요청이라 status 필드가 없다. 사업자가 이 요청 수(FacilityConditionInquiryRepository
// .countByFacility_FacilityId)를 보고 조건 정보를 갱신할 동기를 얻는 용도 — 사업자 대시보드
// 자체는 이 리포에 아직 없어 카운트만 API로 내려준다.
@Getter
@Entity
@Table(name = "facility_condition_inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityConditionInquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    // 선택 입력 — "정확히 뭐가 불명확한지" 같은 부연 설명. 없어도(null) 요청 자체는 유효하다.
    @Column(columnDefinition = "TEXT")
    private String memo;

    @Builder
    private FacilityConditionInquiry(
            User user,
            Facility facility,
            String memo
    ) {
        this.user = user;
        this.facility = facility;
        this.memo = memo;
    }

}
