package com.freepets.domain.review.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.review.entity.ReviewPet;

public interface ReviewPetRepository extends JpaRepository<ReviewPet, Long> {

    /**
     * "취향 비슷한 새곳"(similar)의 {@code matchedByKind}/{@code matchedByBreedSize} 표시용 —
     * 이 시설에 달린(삭제 안 된) 리뷰들이 어떤 반려동물과 함께였는지. 후보 시설 수가 적은 MVP
     * 규모를 전제로 후보마다 호출한다 — 후보가 많아지면 배치 쿼리로 바꿔야 한다.
     */
    @EntityGraph(attributePaths = "pet")
    List<ReviewPet> findAllByReview_Facility_FacilityIdAndReview_DeletedAtIsNull(Long facilityId);

}
