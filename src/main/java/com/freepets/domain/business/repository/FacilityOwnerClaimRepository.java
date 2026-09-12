package com.freepets.domain.business.repository;

import java.util.List;
import java.util.Optional;

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

    /** 한 시설은 한 사업자만 소유하므로 결과는 최대 한 건이다. 매장 등록 시 주인이 이미 있는지 본다. */
    Optional<FacilityOwnerClaim> findByFacility_FacilityId(Long facilityId);

    /**
     * 탈퇴 시 소유 기록을 지운다. 탈퇴는 소프트 삭제라 사용자 행이 남아 외래 키 CASCADE가 동작하지
     * 않는다. 그대로 두면 탈퇴한 계정이 매장을 붙잡고 있어 진짜 사장이 등록하지 못한다.
     */
    void deleteAllByUser_Id(Long userId);
}
