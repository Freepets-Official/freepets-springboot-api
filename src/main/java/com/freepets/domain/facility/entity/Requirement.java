package com.freepets.domain.facility.entity;

/**
 * 반려동물 동반 시 지켜야 하는 출입 요구조건.
 *
 * <p>앞의 여섯은 기능명세서 4.1의 {@code Requirement} 정의를 따르고,
 * {@link #STROLLER}·{@link #MANNER_BELT}는 관광공사 실데이터의
 * {@code acmpyNeedMtr} 원자값에서 확인되어 추가했다.
 *
 * <p>특정 견종·상황에만 적용되는 조건("맹견은 입마개 필수")은 여기에 담지 않는다.
 * 전체에 적용되는 조건으로 잘못 해석되어 판별이 뒤틀리므로, 출입 조건 원문 노출로만 남긴다.
 */
public enum Requirement {

    /** 목줄 착용 */
    LEASH,

    /** 이동장(켄넬) 사용 */
    CAGE,

    /** 입마개 착용 */
    MUZZLE,

    /** 예방접종 증명 */
    VACCINATION,

    /** 소형견만 동반 가능 */
    SMALL_ONLY,

    /** 야외 구역만 동반 가능 */
    OUTDOOR_ONLY,

    /** 반려동물 유모차 탑승 */
    STROLLER,

    /** 매너벨트 착용 */
    MANNER_BELT

}
