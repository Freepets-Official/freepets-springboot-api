package com.freepets.domain.petcheck.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.petcheck.entity.PetCheck;

public interface PetCheckRepository extends JpaRepository<PetCheck, Long> {

    boolean existsByPetPetIdAndFacilityFacilityId(
            Long petId,
            Long facilityId
    );

    // 리뷰 작성 자격 검사용 — 새·토끼처럼 개별 판별이 없는 종도 있어, 반려동물 단위가 아니라
    // "이 유저가 이 시설에서 판별을 한 번이라도 받았는지"로 시설 단위로 확인한다.
    boolean existsByUserIdAndFacilityFacilityId(
            Long userId,
            Long facilityId
    );
}
