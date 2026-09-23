package com.freepets.domain.business.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;

import lombok.RequiredArgsConstructor;

/**
 * 매장 소유 기록 조회. {@link FacilityOwnerClaimCommandService}와 짝을 맞춘 조회 전용 서비스다.
 *
 * <p>{@link BusinessQueryService}는 국세청 호출만 다루는 별개 관심사라 여기 두지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityOwnerClaimQueryService {

    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    /**
     * 내 매장 등록 신청 목록. 상태와 무관하게 전부 최신순으로 내려준다 — 앱의 "심사 중" 화면과
     * 지난 반려 이력 확인에 함께 쓰인다.
     */
    public BusinessResponseDTO.MyClaimList getMyClaims(Long userId) {
        List<FacilityOwnerClaim> claims =
                facilityOwnerClaimRepository.findAllWithFacilityByUserIdOrderByCreatedAtDesc(userId);
        return BusinessConverter.toMyClaimList(claims);
    }
}
