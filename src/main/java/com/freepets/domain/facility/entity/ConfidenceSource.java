package com.freepets.domain.facility.entity;

/**
 * {@link Confidence}의 근거. 지금 실제로 계산해 낼 수 있는 값은 {@code DENIAL_REPORT}와
 * {@code NONE}뿐이다 — {@code OWNER}(사업자 셀프 등록), {@code CROWD}(방문자 제보 다수 일치),
 * {@code USER_CALL}(직접 전화 확인)은 그 기능들이 아직 없어 도달하지 않는다. 나중에 그 기능이
 * 생기면 계산 로직에 분기만 추가하면 되도록 값은 미리 둔다.
 */
public enum ConfidenceSource {
    OWNER,
    CROWD,
    PARSED,
    USER_CALL,
    DENIAL_REPORT,
    NONE
}
