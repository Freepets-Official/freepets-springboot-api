package com.freepets.domain.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReportStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 친화도 점수(0~100) 계산식. {@link com.freepets.domain.review.entity.Review#toScore100()}과
     * 같은 값을 낸다.
     *
     * <p>가중치를 전개하면 {@code 0.35 / 5 * 100 = 7}, {@code 0.30 / 5 * 100 = 6}이라 정수 연산만
     * 남는다. 별점이 1~5 정수이므로 결과도 항상 정수이고, 자바 쪽 {@code Math.round}는 실제로는
     * 아무것도 반올림하지 않는다. 두 경로가 같은 점수를 내야 화면마다 점수가 달라 보이지 않는다.
     */
    String SCORE_100 = "(review.ratingSpace * 7 + review.ratingStaff * 7 + review.ratingAmenity * 6)";

    // 목록 조회에서 리뷰마다 작성자를 따로 불러오면 리뷰 수만큼 쿼리가 늘어나므로 함께 가져온다.
    // 등급·태그 집계가 시설 전체 리뷰를 대상으로 해야 해서 페이지네이션 없이 전부 가져오고,
    // 화면에 내려줄 목록만 서비스에서 10건씩 잘라 쓴다(ReviewQueryService 참고).
    @EntityGraph(attributePaths = "user")
    List<Review> findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(Long facilityId);

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

    /**
     * 시설 한 곳의 리뷰를 DB에서 집계한다. 상세 조회가 쓴다.
     *
     * <p>상세 화면에 필요한 것은 숫자 넷뿐이라 리뷰를 메모리로 올리지 않는다.
     *
     * <p>승인된 신고가 달린 리뷰는 제외한다. 조인 대신 {@code not exists}를 쓰는 이유는, 한 리뷰에
     * 신고가 여러 건 달렸을 때 조인이 행을 늘려 {@code count}와 {@code avg}를 왜곡하기 때문이다.
     * 리뷰 1건은 언제나 1로만 집계돼야 한다.
     *
     * <p>{@code group by}가 중요하다. 적격 리뷰가 없으면 그룹 자체가 생기지 않아 결과가 0행이 된다.
     * 그룹 없이 조회하면 전 컬럼이 null인 행이 하나 나와 원시 타입 언박싱에서 터진다.
     *
     * <p>{@code avg(정수)}는 numeric으로 나오므로 double로 캐스팅한다.
     *
     * @return 적격 리뷰가 없으면 {@code Optional.empty()}
     */
    @Query("select new com.freepets.domain.review.repository.FacilityReviewAggregate("
            + "review.facility.facilityId, "
            + "count(review), "
            + "cast(avg" + SCORE_100 + " as double), "
            + "cast(avg(review.ratingSpace) as double), "
            + "cast(avg(review.ratingStaff) as double), "
            + "cast(avg(review.ratingAmenity) as double)) "
            + "from Review review "
            + "where review.facility.facilityId = :facilityId "
            + "and review.deletedAt is null "
            + "and not exists ("
            + "select report.reportId from ReviewReport report "
            + "where report.review.reviewId = review.reviewId "
            + "and report.status = :excludedStatus) "
            + "group by review.facility.facilityId")
    Optional<FacilityReviewAggregate> aggregateByFacilityId(
            @Param("facilityId") Long facilityId,
            @Param("excludedStatus") ReviewReportStatus excludedStatus
    );

}
