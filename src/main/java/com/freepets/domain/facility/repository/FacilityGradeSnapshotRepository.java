package com.freepets.domain.facility.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.facility.entity.FacilityGradeSnapshot;

public interface FacilityGradeSnapshotRepository extends JpaRepository<FacilityGradeSnapshot, Long> {

    // 스케줄러가 같은 날 다시 돌아도(재시작 등) 중복 적재를 막는다.
    boolean existsByFacility_FacilityIdAndSnapshotDate(Long facilityId, LocalDate snapshotDate);

    // 리뷰·통계 화면의 등급 추이 — from 이후를 오래된 순으로 내려 그래프가 왼쪽부터 그려지게 한다.
    List<FacilityGradeSnapshot> findByFacility_FacilityIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            Long facilityId,
            LocalDate from
    );

    // 보관 기간이 지난 스냅샷 정리 — 스케줄러가 적재 직후에 호출한다.
    void deleteBySnapshotDateBefore(LocalDate cutoff);
}
