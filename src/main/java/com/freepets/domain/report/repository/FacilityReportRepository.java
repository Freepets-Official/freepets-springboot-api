package com.freepets.domain.report.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.report.entity.FacilityReport;

public interface FacilityReportRepository extends JpaRepository<FacilityReport, Long> {

    // 원터치 제보 남용 방지 — 같은 유저·시설 조합은 24시간에 1건만 허용한다.
    boolean existsByUser_IdAndFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(
            Long userId,
            Long facilityId,
            LocalDateTime after
    );

    // 3건 이상 쌓이면 로그로 남긴다(관리자 승격 API 자체가 없어 지금은 로그가 전부다) — DenialReportCommandService 참고.
    // 신뢰도 계산(FacilityQueryService)도 "최근 실시간 거부가 있는지"를 볼 때 이 카운트를 재사용한다.
    long countByFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(
            Long facilityId,
            LocalDateTime after
    );

    // GET .../denial-reports/recent — 타인의 제보만, 최신순. 최대 3건은 Pageable로 자른다.
    List<FacilityReport> findAllByFacility_FacilityIdAndIsRealtimeTrueAndUser_IdNotAndCreatedAtAfterOrderByCreatedAtDesc(
            Long facilityId,
            Long userId,
            LocalDateTime after,
            Pageable pageable
    );

    // GET .../denial-reports/mine — 내가 이 시설에 보낸 실시간 제보(만료 없이 항상 최신 1건).
    Optional<FacilityReport> findFirstByFacility_FacilityIdAndUser_IdAndIsRealtimeTrueOrderByCreatedAtDesc(
            Long facilityId,
            Long userId
    );

    // GET /me/denial-alerts — 여러 시설을 한 번에 훑어야 해서 IN절로 조회한다. 응답이
    // facility.name을 그대로 쓰는데(DenialReportConverter.toDenialAlert), facility가 지연
    // 로딩이라 JOIN FETCH 없이 쓰면 시설 수만큼 추가 쿼리가 나간다 — 미리 함께 가져온다.
    @Query("""
            SELECT r FROM FacilityReport r
            JOIN FETCH r.facility
            WHERE r.facility.facilityId IN :facilityIds
              AND r.isRealtime = true
              AND r.user.id <> :userId
              AND r.createdAt > :after
            ORDER BY r.createdAt DESC
            """)
    List<FacilityReport> findAllByFacility_FacilityIdInAndIsRealtimeTrueAndUser_IdNotAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("facilityIds") List<Long> facilityIds,
            @Param("userId") Long userId,
            @Param("after") LocalDateTime after
    );
}
