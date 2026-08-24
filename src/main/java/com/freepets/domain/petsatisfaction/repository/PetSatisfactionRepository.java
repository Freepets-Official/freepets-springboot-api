package com.freepets.domain.petsatisfaction.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;

public interface PetSatisfactionRepository extends JpaRepository<PetSatisfaction, Long> {

    Optional<PetSatisfaction> findByPetPetIdAndFacilityFacilityId(
            Long petId,
            Long facilityId
    );

    // 시설 하나에 대해 내 반려동물들 중 이미 기록이 있는 것만 가져와서, 서비스에서
    // "기록 전" 반려동물과 합친다.
    @EntityGraph(attributePaths = "pet")
    List<PetSatisfaction> findAllByFacilityFacilityIdAndPetPetIdIn(
            Long facilityId,
            Collection<Long> petIds
    );

    // 홈 "아이별 좋아한 곳 TOP" 계산용 — 삭제된 반려동물의 기록은 제외한다.
    @EntityGraph(attributePaths = {"pet", "facility"})
    List<PetSatisfaction> findAllByPetUserIdAndPetDeletedAtIsNull(Long userId);
}
