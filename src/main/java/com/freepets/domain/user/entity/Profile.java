package com.freepets.domain.user.entity;

import java.util.List;

/**
 * 한 계정이 골라 쓸 수 있는 화면 세트. 가입하면 전원 {@code CONSUMER}이고, 매장 소유 인증을 마쳐
 * 소유 기록이 생기면 {@code OWNER}가 저절로 붙는다. 저장하지 않고 조회할 때마다 파생한다 —
 * 토큰에 넣으면 매장 등록 직후 재로그인해야 반영되기 때문이다.
 *
 * <p><b>권한이 아니다.</b> 지금 어느 프로필을 쓰는지는 앱 UI 상태일 뿐 서버로 오지 않는다.
 * 사업자 기능을 써도 되는지는 {@code owner/**} 요청마다 소유 기록으로 따로 확인한다.
 */
public enum Profile {
    CONSUMER,
    OWNER;

    public static List<Profile> of(List<Long> ownedFacilityIds) {
        return ownedFacilityIds.isEmpty()
                ? List.of(CONSUMER)
                : List.of(CONSUMER, OWNER);
    }
}
