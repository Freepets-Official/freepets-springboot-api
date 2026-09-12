package com.freepets.domain.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.business.entity.FacilityOwnerClaim;

public interface FacilityOwnerClaimRepository extends JpaRepository<FacilityOwnerClaim, Long> {

    /**
     * 사용자가 소유한 시설 ID를 소유 기록이 생긴 순서대로 반환한다. 비어 있지 않으면 곧 사업자
     * 프로필이 있다는 뜻이라, 소유 매장 목록과 프로필 파생을 이 쿼리 하나로 처리한다.
     */
    @Query("select claim.facility.facilityId from FacilityOwnerClaim claim"
            + " where claim.user.id = :userId order by claim.claimId")
    List<Long> findFacilityIdsByUserId(@Param("userId") Long userId);
}
