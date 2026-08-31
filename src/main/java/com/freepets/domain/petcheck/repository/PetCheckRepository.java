package com.freepets.domain.petcheck.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
