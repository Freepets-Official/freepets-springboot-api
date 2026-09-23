package com.freepets.domain.business.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * {@code owner/**} 요청의 소유자 검증. 사업자 대시보드의 모든 API가 이 한 곳을 거친다.
 *
 * <p>프로필(소비자/사업자)은 화면 세트일 뿐 권한이 아니다. 앱이 지금 어느 프로필을 쓰는지는 서버로
 * 오지 않으므로, <b>요청마다</b> 소유 기록으로 요청자가 그 매장의 주인인지 확인한다. 검사를 각
 * 서비스에 흩어 두면 한 곳만 빠뜨려도 남의 매장 데이터가 새기 때문에 여기로 모은다.
 *
 * <p>승인된 기록만 주인으로 본다. 심사 중이거나 반려·해제된 신청은 소유권이 아니다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityOwnershipValidator {

    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    /**
     * 요청자가 이 매장의 주인이 아니면 403으로 막는다.
     *
     * <p>시설이 존재하는지는 따로 보지 않는다 — 없는 시설에는 승인된 소유 기록도 없어 어차피 여기서
     * 걸린다. 404와 403을 구분하면 소유자가 아닌 사람에게 "그 시설이 있긴 하다"를 알려주게 되는데,
     * 사업자 경로에서는 알려줄 이유가 없다.
     */
    public void requireOwner(
            Long userId,
            Long facilityId
    ) {
        if (!facilityOwnerClaimRepository.existsApprovedByFacilityIdAndUserId(facilityId, userId)) {
            throw new GeneralException(ErrorStatus.BUSINESS4008);
        }
    }
}
