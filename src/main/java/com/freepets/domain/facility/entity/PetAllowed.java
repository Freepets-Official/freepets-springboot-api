package com.freepets.domain.facility.entity;

/**
 * 시설의 반려동물 동반 가능 여부.
 *
 * <p>우리 아이 기준 판별 결과({@link com.freepets.domain.petcheck.entity.PetCheckResult})와는
 * 다른 축이다. 이 값은 시설의 사실 데이터이고, 판별 결과는 그것을 입력으로 삼아 계산된다.
 */
public enum PetAllowed {

    /** 동반 가능. 관광공사 반려동물 동반 정보를 보유한 시설 */
    ALLOWED,

    /** 동반 불가. 원문에 명시적 불가 표현이 있거나 사업자가 불가로 확정한 시설 */
    DENIED,

    /** 확인 필요. 반려동물 동반 정보 자체가 없는 시설 */
    PENDING

}
