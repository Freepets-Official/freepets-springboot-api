package com.freepets.domain.review.repository;

/**
 * 시설 한 곳의 리뷰 집계 결과.
 *
 * <p>승인된 신고가 달린 리뷰는 빠진 값이다. 적격 리뷰가 한 건도 없으면 이 record 자체가
 * 만들어지지 않는다({@link ReviewRepository#aggregateByFacilityId} 참고).
 */
public record FacilityReviewAggregate(
        Long facilityId,
        long reviewCount,

        /** 친화도 점수 0~100. */
        double averageScore,

        /** 별점 평균 1~5. */
        double averageSpace,
        double averageStaff,
        double averageAmenity
) {}
