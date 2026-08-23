package com.freepets.domain.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 목록 조회에서 리뷰마다 작성자를 따로 불러오면 리뷰 수만큼 쿼리가 늘어나므로 함께 가져온다.
    @EntityGraph(attributePaths = "user")
    List<Review> findAllByFacilityFacilityIdAndDeletedAtIsNull(Long facilityId);

    Optional<Review> findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(
            Long facilityId,
            Long userId
    );

    Optional<Review> findByReviewIdAndDeletedAtIsNull(Long reviewId);

    /**
     * 시설별 리뷰 수를 한 번에 센다.
     *
     * <p>목록 조회에서 시설마다 count를 날리면 페이지당 쿼리가 시설 수만큼 늘어나므로 묶어서 센다.
     * 리뷰가 하나도 없는 시설은 결과에 나타나지 않으니 호출부에서 0으로 채워야 한다.
     */
    @Query("select new com.freepets.domain.review.repository.FacilityReviewCount("
            + "review.facility.facilityId, count(review)) "
            + "from Review review "
            + "where review.facility.facilityId in :facilityIds "
            + "and review.deletedAt is null "
            + "group by review.facility.facilityId")
    List<FacilityReviewCount> countByFacilityIds(@Param("facilityIds") Collection<Long> facilityIds);

}
