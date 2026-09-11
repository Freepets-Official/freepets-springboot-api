package com.freepets.domain.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.report.dto.FacilityConditionInquiryResponseDTO;
import com.freepets.domain.report.repository.FacilityConditionInquiryRepository;

import lombok.RequiredArgsConstructor;

// GET /api/v1/facilities/{facilityId}/condition-inquiries/count — 사업자 대시보드가 아직
// 없어서, 지금은 이 카운트를 직접 조회할 수 있는 API만 열어둔다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityConditionInquiryQueryService {

    private final FacilityConditionInquiryRepository facilityConditionInquiryRepository;
    private final FacilityRepository facilityRepository;

    public FacilityConditionInquiryResponseDTO.CountResult getCount(Long facilityId) {
        FacilityExistenceValidator.requireFacility(facilityRepository, facilityId);

        long count = facilityConditionInquiryRepository.countByFacility_FacilityId(facilityId);
        return new FacilityConditionInquiryResponseDTO.CountResult(facilityId, count);
    }
}
