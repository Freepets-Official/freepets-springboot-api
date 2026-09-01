package com.freepets.domain.petsatisfaction.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * {@code GET /api/v1/courses/liked} 후보 풀 산출용 — 선택한 petIds 중 하나라도 방문 기록을
     * 남긴 시설을 찾을 때, 그 기록 자체(=reasonPets 구성 재료)도 같이 가져온다.
     */
    @EntityGraph(attributePaths = {"pet", "facility"})
    List<PetSatisfaction> findAllByPetPetIdIn(Collection<Long> petIds);

    /**
     * 후보 시설들에 대해 {@code petIds}로 좁히지 않은 전체 반려동물 평균 — {@code avgSatisfaction}
     * 계산 및 6.5 이상 필터링에 쓴다({@code liked}는 후보 풀과 필터가 서로 다른 평균을 쓴다).
     */
    @Query("""
            select facility.facilityId as facilityId, avg(petSatisfaction.score) as avgScore
            from PetSatisfaction petSatisfaction
            join petSatisfaction.facility facility
            where facility.facilityId in :facilityIds
            group by facility.facilityId
            """)
    List<FacilityAverageSatisfaction> findAverageScoreByFacilityIdIn(@Param("facilityIds") Collection<Long> facilityIds);
}
