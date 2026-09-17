package com.freepets.domain.business.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * 사업자의 매장 소개·홍보 저장. 소개글·편의시설 태그는 판별에 쓰이지 않아 {@code confirmedAt}이나
 * 추천 자격과 무관하다 — {@link OwnerFacilityConditionCommandService}와 달리 동시성으로 값이
 * 어긋날 판별 로직이 없어 비관적 락 없이 {@code findById}로 조회한다.
 *
 * <p>시설 ID를 인자로 받으므로 {@link FacilityOwnershipValidator}를 가장 먼저 호출한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerFacilityProfileCommandService {

    private final FacilityOwnershipValidator facilityOwnershipValidator;
    private final FacilityRepository facilityRepository;

    public BusinessResponseDTO.FacilityProfile updateProfile(
            Long userId,
            Long facilityId,
            BusinessRequestDTO.FacilityProfileUpdateRequest request
    ) {
        facilityOwnershipValidator.requireOwner(userId, facilityId);

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        facility.updateProfile(request.getIntroduction(), request.getAmenityTags());

        return BusinessConverter.toFacilityProfile(facility);
    }
}
