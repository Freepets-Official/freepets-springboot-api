package com.freepets.domain.business.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.business.entity.FacilityOwnerClaim;

public interface FacilityOwnerClaimRepository extends JpaRepository<FacilityOwnerClaim, Long> {

    /**
     * 사용자가 소유한 시설 ID를 소유 기록이 생긴 순서대로 반환한다. <b>승인된 기록만</b> 센다 — 심사 중이거나
     * 반려·해제된 매장은 소유 매장이 아니다. 비어 있지 않으면 곧 사업자 프로필이 있다는 뜻이라, 소유 매장
     * 목록과 프로필 파생을 이 쿼리 하나로 처리한다.
     */
    @Query("select claim.facility.facilityId from FacilityOwnerClaim claim"
            + " where claim.user.id = :userId"
            + " and claim.status = com.freepets.domain.business.entity.ClaimStatus.APPROVED"
            + " order by claim.claimId")
    List<Long> findApprovedFacilityIdsByUserId(@Param("userId") Long userId);

    /**
     * 시설의 승인된 소유 기록을 찾는다. 한 시설에 승인된 사업자는 하나뿐이라 결과는 최대 한 건이다.
     * 매장 등록 시 주인이 이미 있는지 본다.
     *
     * <p>상태를 인자로 받지 않는다 — 대기 신청은 한 시설에 여러 개일 수 있어, 다른 상태로 부르면 결과가
     * 두 건 이상이 되어 조회 자체가 실패한다.
     */
    @Query("select claim from FacilityOwnerClaim claim"
            + " where claim.facility.facilityId = :facilityId"
            + " and claim.status = com.freepets.domain.business.entity.ClaimStatus.APPROVED")
    Optional<FacilityOwnerClaim> findApprovedByFacilityId(@Param("facilityId") Long facilityId);

    /**
     * 요청자가 이 시설에 이미 심사 중인 신청을 냈는지. 같은 사람이 같은 매장에 신청을 여러 번 쌓지 못하게 한다
     * (남이 낸 대기 신청은 막지 않는다 — 먼저 신청했다고 선점하면 안 된다).
     */
    @Query("select count(claim) > 0 from FacilityOwnerClaim claim"
            + " where claim.facility.facilityId = :facilityId"
            + " and claim.user.id = :userId"
            + " and claim.status = com.freepets.domain.business.entity.ClaimStatus.PENDING")
    boolean existsPendingByFacilityIdAndUserId(
            @Param("facilityId") Long facilityId,
            @Param("userId") Long userId
    );

    /**
     * 요청자의 모든 신청을 최신순으로, 시설과 함께 가져온다. 상태로 거르지 않는다 — 지난 반려 이력도
     * 화면에서 보여줄 수 있어야 한다. 응답이 시설명·주소를 그대로 쓰는데 facility가 지연 로딩이라
     * JOIN FETCH 없이 쓰면 신청 수만큼 추가 쿼리가 나간다(FacilityReportRepository의
     * findAllByFacility_FacilityIdIn...과 같은 이유).
     */
    @Query("""
            SELECT claim FROM FacilityOwnerClaim claim
            JOIN FETCH claim.facility
            WHERE claim.user.id = :userId
            ORDER BY claim.createdAt DESC
            """)
    List<FacilityOwnerClaim> findAllWithFacilityByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * 탈퇴 시 소유 기록을 지운다. 탈퇴는 소프트 삭제라 사용자 행이 남아 외래 키 CASCADE가 동작하지
     * 않는다. 그대로 두면 탈퇴한 계정이 매장을 붙잡고 있어 진짜 사장이 등록하지 못한다.
     */
    void deleteAllByUser_Id(Long userId);
}
