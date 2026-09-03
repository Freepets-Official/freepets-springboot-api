package com.freepets.domain.review.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.review.entity.ReviewPet;

public interface ReviewPetRepository extends JpaRepository<ReviewPet, Long> {

    /**
     * "취향 비슷한 새곳"(similar)의 {@code matchedByKind}/{@code matchedByBreedSize} 채점·표시용 —
     * 후보 시설 여러 곳에 달린(삭제 안 된) 리뷰가 어떤 반려동물과 함께였는지 한 번에 묶어 조회한다.
     * 예전엔 후보마다 따로 호출해 후보 수만큼 쿼리가 늘어났다(N+1) — 채점 단계에서 모든 후보의
     * 점수를 매겨야 해서 더는 미룰 수 없는 문제라 배치 쿼리로 바꿨다. 호출부에서 facilityId별로
     * 다시 그룹핑해 쓴다.
     */
    @Query("""
            select new com.freepets.domain.review.repository.FacilityPetProfile(
                reviewPet.review.facility.facilityId, reviewPet.pet.kind, reviewPet.pet.breedSize)
            from ReviewPet reviewPet
            where reviewPet.review.facility.facilityId in :facilityIds
            and reviewPet.review.deletedAt is null
            """)
    List<FacilityPetProfile> findKindAndBreedSizeByFacilityIdIn(@Param("facilityIds") Collection<Long> facilityIds);

}
