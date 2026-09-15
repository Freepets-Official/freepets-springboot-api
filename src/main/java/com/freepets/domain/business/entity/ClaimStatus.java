package com.freepets.domain.business.entity;

/**
 * 매장 소유 기록의 심사 상태. 소유권·사업자 프로필·중복 등록 검사는 {@code APPROVED}만 인정한다.
 *
 * <p>국세청 진위확인은 "요청자가 그 사업자와 관계있다"까지만 증명하고 "그 사업자가 이 매장"은
 * 증명하지 못해서, 운영자가 사업자등록증을 대조해 승인한 뒤에야 소유권을 준다.
 */
public enum ClaimStatus {
    /** 심사 대기. 같은 매장에 여러 사람이 동시에 신청할 수 있어 먼저 신청했다고 선점하지 못한다. */
    PENDING,
    APPROVED,
    REJECTED,
    /** 승인을 푼 상태(이의 제기 등). 행을 지우지 않고 상태만 바꿔 이력을 남긴다. */
    REVOKED
}
