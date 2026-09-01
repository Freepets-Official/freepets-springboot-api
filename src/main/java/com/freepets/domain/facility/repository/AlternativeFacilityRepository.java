package com.freepets.domain.facility.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.facility.entity.AlternativeFacility;

public interface AlternativeFacilityRepository extends JpaRepository<AlternativeFacility, Long> {

    /**
     * {@code course-check}(POST /api/v1/ai/course-check)가 DENIED 스톱마다 대안을 찾을 때 쓴다.
     * 가까운 순으로 제안한다.
     */
    List<AlternativeFacility> findAllByFacilityFacilityIdOrderByDistanceKmAsc(Long facilityId);

}
