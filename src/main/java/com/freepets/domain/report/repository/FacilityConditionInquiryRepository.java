package com.freepets.domain.report.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.report.entity.FacilityConditionInquiry;

public interface FacilityConditionInquiryRepository extends JpaRepository<FacilityConditionInquiry, Long> {

    // 남용 방지 — 같은 유저·시설 조합은 24시간에 1건만 허용한다(DenialReport와 동일 패턴).
    boolean existsByUser_IdAndFacility_FacilityIdAndCreatedAtAfter(
            Long userId,
            Long facilityId,
            LocalDateTime after
    );

    // GET .../condition-inquiries/count — 이 시설에 쌓인 전체 요청 수. 사업자 대시보드가 생기면
    // 그쪽에서 이 값을 읽어가면 된다.
    long countByFacility_FacilityId(Long facilityId);

}
