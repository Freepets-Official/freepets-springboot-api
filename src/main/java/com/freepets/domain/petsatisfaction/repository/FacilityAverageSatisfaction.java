package com.freepets.domain.petsatisfaction.repository;

/**
 * 시설 하나에 대한 전체 반려동물(요청 petIds로 안 좁힘) 평균 만족도.
 * {@code GET /api/v1/courses/liked}의 {@code avgSatisfaction} 계산·필터링에 쓴다.
 */
public interface FacilityAverageSatisfaction {

    Long getFacilityId();

    Double getAvgScore();

}
