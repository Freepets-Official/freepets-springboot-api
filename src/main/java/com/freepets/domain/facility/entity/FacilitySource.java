package com.freepets.domain.facility.entity;

/**
 * 시설 데이터의 출처.
 */
public enum FacilitySource {

    /** 한국관광공사 국문 관광정보 서비스에서 적재한 시설 */
    TOUR_API,

    /** 사업자가 직접 등록한 시설. 관광공사 콘텐츠 ID가 없다 */
    BUSINESS_SELF

}
