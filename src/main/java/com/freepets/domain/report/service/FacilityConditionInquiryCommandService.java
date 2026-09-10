package com.freepets.domain.report.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.report.converter.FacilityConditionInquiryConverter;
import com.freepets.domain.report.dto.FacilityConditionInquiryResponseDTO;
import com.freepets.domain.report.entity.FacilityConditionInquiry;
import com.freepets.domain.report.repository.FacilityConditionInquiryRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

// POST /api/v1/facilities/{facilityId}/condition-inquiries — "이 시설 조건이 불명확해요" 요청.
// DenialReportCommandService(F4)와 같은 24시간 남용 방지 패턴이지만, 검토·승격 절차가 없다 —
// 쌓인 개수 자체가 사업자에게 보여줄 신호의 전부다.
@Service
@RequiredArgsConstructor
@Transactional
public class FacilityConditionInquiryCommandService {

    // DenialReportCommandService.RATE_LIMIT_HOURS와 같은 이유 — 같은 유저·시설 조합의 남용을 막는 창.
    private static final int RATE_LIMIT_HOURS = 24;

    private final FacilityConditionInquiryRepository facilityConditionInquiryRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public FacilityConditionInquiryResponseDTO.InquireResult inquire(
            Long userId,
            Long facilityId,
            String memo
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        LocalDateTime rateLimitSince = LocalDateTime.now().minusHours(RATE_LIMIT_HOURS);
        boolean alreadyRequestedRecently = facilityConditionInquiryRepository
                .existsByUser_IdAndFacility_FacilityIdAndCreatedAtAfter(userId, facilityId, rateLimitSince);
        if (alreadyRequestedRecently) {
            throw new GeneralException(ErrorStatus.REPORT4002);
        }

        FacilityConditionInquiry saved = facilityConditionInquiryRepository.save(
                FacilityConditionInquiry.builder()
                        .user(user)
                        .facility(facility)
                        .memo(memo)
                        .build()
        );

        return FacilityConditionInquiryConverter.toInquireResult(saved);
    }
}
