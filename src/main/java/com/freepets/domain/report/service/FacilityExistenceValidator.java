package com.freepets.domain.report.service;

import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

// DenialReportQueryService·FacilityConditionInquiryQueryService가 조회 진입점에서 똑같이
// "이 시설이 존재하는가"만 확인하는 코드였다 — 한쪽만 고치면 갈릴 수 있어 여기로 모은다.
final class FacilityExistenceValidator {

    private FacilityExistenceValidator() {}

    static void requireFacility(
            FacilityRepository facilityRepository,
            Long facilityId
    ) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new GeneralException(ErrorStatus.FACILITY4001);
        }
    }
}
