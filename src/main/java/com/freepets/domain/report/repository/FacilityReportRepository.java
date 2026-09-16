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

    /*
     * 아래 두 쿼리는 "지금 신뢰도를 내리고 있는 실시간 거부 제보"라는 같은 조건을 쓴다. 사업자 대시보드
     * 홈이 매장마다 제보 건수와 최신 제보 한 줄을 함께 그리는데, 건수만 필요한 자리에서 제보 행을 전부
     * 메모리로 올리지 않으려고 집계와 최신 1건을 나눴다.
     *
     * 고르는 기준은 FacilityQueryService.denialReportSince와 같은 max(confirmedAt, since)다 —
     * since(최근 7일)보다 뒤이면서, 사업자가 조건을 확정했다면 그 시각보다도 뒤인 제보만 본다. 확정 이전
     * 제보는 사장님이 조건을 바로잡으면서 해소된 것으로 본다. 확정한 적이 없는 시설(confirmedAt IS NULL)은
     * since만 본다 — null 비교는 참이 되지 않아 조건을 따로 적어주지 않으면 그 시설이 통째로 빠진다.
     *
     * 이 기준은 배지 계산(Confidence.of)이 쓰는 것과 같아야 한다. 어긋나면 사장님 화면에는 제보가 없는데
     * 배지만 내려가 있는 상태가 생긴다. 두 쿼리의 WHERE가 갈라지면 건수와 최신 제보도 서로 어긋나므로,
     * 한쪽을 고치면 반드시 다른 쪽도 같이 고쳐야 한다.
     */

    /** 시설별 제보 수. 시설당 한 행만 나오므로 제보가 아무리 쌓여도 읽는 양이 늘지 않는다. */
    @Query("""
            SELECT new com.freepets.domain.report.repository.FacilityDenialReportCount(
                       r.facility.facilityId, COUNT(r))
            FROM FacilityReport r
            WHERE r.facility.facilityId IN :facilityIds
              AND r.isRealtime = true
              AND r.createdAt > :since
              AND (r.facility.confirmedAt IS NULL OR r.createdAt > r.facility.confirmedAt)
            GROUP BY r.facility.facilityId
            """)
    List<FacilityDenialReportCount> countDowngradingByFacilityIds(
            @Param("facilityIds") List<Long> facilityIds,
            @Param("since") LocalDateTime since
    );

    /**
     * 시설별 <b>가장 최근</b> 제보 한 건. 경고 카드의 "현장 거부 · 실내 불가 · 23분 전" 한 줄에 쓴다.
     *
     * <p>같은 조건을 서브쿼리에 한 번 더 적는 것은 JPQL에서 피할 수 없다 — 시설별 최댓값을 먼저 구해야
     * 그 행을 고를 수 있다. 같은 시설에 제보 시각이 완전히 같은 두 건이 있으면 두 행이 나오므로, 호출부는
     * 중복 키를 견디게 모아야 한다.
     */
    @Query("""
            SELECT new com.freepets.domain.report.repository.DowngradingDenialReport(
                       r.facility.facilityId, r.denialReason, r.createdAt)
            FROM FacilityReport r
            WHERE r.facility.facilityId IN :facilityIds
              AND r.isRealtime = true
              AND r.createdAt > :since
              AND (r.facility.confirmedAt IS NULL OR r.createdAt > r.facility.confirmedAt)
              AND r.createdAt = (
                  SELECT MAX(latest.createdAt) FROM FacilityReport latest
                  WHERE latest.facility.facilityId = r.facility.facilityId
                    AND latest.isRealtime = true
                    AND latest.createdAt > :since
                    AND (latest.facility.confirmedAt IS NULL
                         OR latest.createdAt > latest.facility.confirmedAt)
              )
            """)
    List<DowngradingDenialReport> findLatestDowngradingByFacilityIds(
            @Param("facilityIds") List<Long> facilityIds,
            @Param("since") LocalDateTime since
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

    // GET /api/v1/owner/facilities/{facilityId}/denial-alerts — 거부 제보 전체 조회.
    // countDowngradingByFacilityIds / findLatestDowngradingByFacilityIds와 같은 기준
    // (isRealtime=true, since 이후, confirmedAt 이후)을 쓴다. 어긋나면 홈의 건수와 이 화면의
    // 목록이 맞지 않는다. content를 내려야 해서 프로젝션이 아니라 엔티티를 그대로 셀렉트한다.
    @Query("""
            SELECT r FROM FacilityReport r
            WHERE r.facility.facilityId = :facilityId
              AND r.isRealtime = true
              AND r.createdAt > :since
              AND (r.facility.confirmedAt IS NULL OR r.createdAt > r.facility.confirmedAt)
            ORDER BY r.createdAt DESC
            """)
    List<FacilityReport> findDowngradingByFacilityId(
            @Param("facilityId") Long facilityId,
            @Param("since") LocalDateTime since
    );
}
