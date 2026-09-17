package com.freepets.domain.facility.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.facility.entity.FacilityBenefit;

public interface FacilityBenefitRepository extends JpaRepository<FacilityBenefit, Long> {

    // 사장님 관리 화면 — on/off 상관없이 등록순으로 전부 보여준다.
    List<FacilityBenefit> findAllByFacility_FacilityIdOrderByCreatedAtAsc(Long facilityId);

    // 손님 노출용 — 켜진 것만 등록순으로 내려준다.
    List<FacilityBenefit> findAllByFacility_FacilityIdAndIsEnabledTrueOrderByCreatedAtAsc(Long facilityId);

    // 삭제·토글 시 경로의 facilityId와 실제 소유 시설이 일치하는지 함께 확인한다.
    Optional<FacilityBenefit> findByFacilityBenefitIdAndFacility_FacilityId(Long facilityBenefitId, Long facilityId);
}
