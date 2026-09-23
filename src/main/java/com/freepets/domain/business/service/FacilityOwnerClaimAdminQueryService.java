package com.freepets.domain.business.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 심사 목록 조회. 자영업자 self-service용 {@link FacilityOwnerClaimQueryService}와는 호출 주체(관리자)와
 * 권한이 달라 클래스를 분리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityOwnerClaimAdminQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    /**
     * 상태별 신청 목록을 오래된 순으로 내려준다. 같은 매장의 다른 대기 신청을 자동 반려하지 않기로 해서,
     * 목록의 각 항목에 "이미 승인된 소유자가 있는지"({@code hasApprovedOwner})를 함께 표시한다.
     */
    public BusinessResponseDTO.AdminClaimList getClaims(
            ClaimStatus status,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        // 0 이하로 오면 기본값으로, 상한을 넘으면 MAX_PAGE_SIZE로 잘라낸다.
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        Page<FacilityOwnerClaim> claimPage =
                facilityOwnerClaimRepository.findByStatus(status, PageRequest.of(safePage, safeSize));

        List<Long> facilityIds = claimPage.getContent().stream()
                .map(claim -> claim.getFacility().getFacilityId())
                .toList();
        Set<Long> approvedFacilityIds = facilityIds.isEmpty()
                ? Set.of()
                : Set.copyOf(facilityOwnerClaimRepository.findApprovedFacilityIdsIn(facilityIds));

        return BusinessConverter.toAdminClaimList(claimPage, approvedFacilityIds);
    }
}
