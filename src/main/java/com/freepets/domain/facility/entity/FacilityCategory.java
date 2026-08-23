package com.freepets.domain.facility.entity;

/**
 * 시설 분류.
 *
 * <p>관광공사 {@code contentTypeId}와의 매핑은 도메인이 외부 코드에 끌려가지 않도록
 * {@code infra.tourapi} 계층에서 수행한다.
 *
 * <p>교통(관광공사 {@code contentTypeId=77})은 국문 관광정보 서비스에 데이터가 없어 제외했다.
 */
public enum FacilityCategory {

    /** 관광지 */
    TOUR,

    /** 문화시설 */
    CULTURE,

    /** 축제·공연·행사 */
    FESTIVAL,

    /** 레포츠 */
    LEISURE,

    /** 숙박 */
    STAY,

    /** 쇼핑 */
    SHOPPING,

    /** 음식점 */
    RESTAURANT,

    /** 카페 */
    CAFE

}
