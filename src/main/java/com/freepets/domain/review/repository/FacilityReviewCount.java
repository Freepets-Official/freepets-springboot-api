package com.freepets.domain.review.repository;

/** 시설별 리뷰 수 집계 결과. */
public record FacilityReviewCount(
        Long facilityId,
        long reviewCount
) {}
