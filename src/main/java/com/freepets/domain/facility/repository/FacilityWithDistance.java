package com.freepets.domain.facility.repository;

import com.freepets.domain.facility.entity.Facility;

/**
 * 시설과 사용자 위치로부터의 거리를 함께 담는 조회 결과.
 *
 * <p>거리는 요청마다 달라져 저장할 수 없으므로 조회 시점에 계산된다.
 * {@link FacilityRepository}의 HQL 생성자 표현식이 이 타입을 만든다.
 */
public record FacilityWithDistance(
        Facility facility,
        double distanceMeter
) {}
