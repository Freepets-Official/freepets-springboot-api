package com.freepets.domain.facility.entity;

/**
 * 사장님이 직접 선언하는 반려동물 편의시설 태그(매장 소개·홍보 화면). 방문자가 남기는 후기 태그인
 * {@code Tag}와는 축이 다르다 — 같은 값으로 합치면 "사장님 주장"과 "손님 관측"이 구분되지 않는다.
 */
public enum FacilityAmenity {
    WATER_BOWL,
    POOP_BAG,
    PET_MENU,
    OUTDOOR_TERRACE,
    LEASH_FREE_ZONE,
    PARKING,
    PET_SUPPLIES,
    CUSHION_BLANKET
}
