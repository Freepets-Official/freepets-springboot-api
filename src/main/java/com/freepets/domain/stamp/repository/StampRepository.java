package com.freepets.domain.stamp.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.stamp.entity.Stamp;

public interface StampRepository extends JpaRepository<Stamp, Long> {

    Optional<Stamp> findByUser_IdAndFacility_FacilityId(
            Long userId,
            Long facilityId
    );

    List<Stamp> findAllByUser_IdOrderByStampedAtDesc(Long userId);

    long countByUser_Id(Long userId);

    long countByUser_IdAndVerifiedOnSiteTrue(Long userId);

    long countByUser_IdAndStampedAtGreaterThanEqual(
            Long userId,
            LocalDateTime start
    );

    /**
     * 이 사용자가 이 시/군/구에 도장을 찍은 적이 있는지 — {@code StampCommandService}가 새
     * 도장을 저장하기 "전에" 호출해 "이번이 이 지역의 첫 도장인지"({@code regionCompleted})를
     * 판단한다. 저장 후에 확인하면 방금 넣은 도장 자신이 걸려 항상 true가 나온다.
     */
    boolean existsByUser_IdAndSidoCodeAndSigunguCode(
            Long userId,
            String sidoCode,
            String sigunguCode
    );

    /**
     * 이 사용자가 도장을 찍은 서로 다른 시/군/구 쌍. distinct 개수만 필요하지만, 문자열을
     * 이어붙여 세는 방식(예: {@code concat(sidoCode, sigunguCode)})은 구분자 없이 겹치는
     * 값(예: "51" + "150" vs "5" + "1150")이 생길 수 있어 두 컬럼을 그대로 받아 서비스에서
     * 쌍으로 센다.
     */
    @Query("select distinct s.sidoCode as sidoCode, s.sigunguCode as sigunguCode "
            + "from Stamp s where s.user.id = :userId")
    List<RegionKey> findDistinctRegionsByUser_Id(@Param("userId") Long userId);

    interface RegionKey {
        String getSidoCode();

        String getSigunguCode();
    }

}
