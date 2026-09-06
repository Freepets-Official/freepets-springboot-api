package com.freepets.domain.petcheck.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.petcheck.entity.PetCheck;

public interface PetCheckRepository extends JpaRepository<PetCheck, Long> {

    Page<PetCheck> findAllByUser_IdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    Page<PetCheck> findAllByUser_IdAndFacility_FacilityIdOrderByCreatedAtDesc(
            Long userId,
            Long facilityId,
            Pageable pageable
    );

    // 리뷰 작성 자격 검사(POST /api/v1/reviews, ReviewCommandService)가 쓴다 — 그룹 판별이라
    // 반려동물 단위가 아니라 "이 유저가 이 시설에서 판별을 한 번이라도 받았는지"로 확인한다.
    boolean existsByUserIdAndFacilityFacilityId(
            Long userId,
            Long facilityId
    );

    // GET /me/denial-alerts(DenialReportQueryService)가 쓴다 — "내가 판별받은(가려던) 시설"을
    // 위치(GPS)가 아니라 판별 이력으로 정의한다. 시설 하나를 여러 번 판별했을 수 있어 GROUP BY로
    // 중복을 없앤다. 판별 이력이 아주 많은 유저의 IN절이 무한정 커지는 걸 막기 위해 Pageable로
    // 상한을 두고, 어차피 자를 거면 최근에 판별한 시설이 남도록 MAX(createdAt) 내림차순으로 정렬한다
    // — DISTINCT는 표준 SQL상 ORDER BY 대상이 SELECT 목록에 없으면 못 쓴다.
    @Query("""
            SELECT pc.facility.facilityId FROM PetCheck pc
            WHERE pc.user.id = :userId
            GROUP BY pc.facility.facilityId
            ORDER BY MAX(pc.createdAt) DESC
            """)
    List<Long> findDistinctFacilityIdsByUser_Id(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
