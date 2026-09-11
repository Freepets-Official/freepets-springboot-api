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
// DenialReportCommandService(F4)와 같은 24시간 남용 방지 창을 쓰지만, 검토·승격 절차가 없어
// 쌓인 개수 자체가 사업자에게 보여줄 신호의 전부다 — 그래서 DenialReportCommandService가
// 받아들인 "exists 확인과 save 사이의 레이스"를 여기서는 그대로 받아들이지 않고, 대상 시설
// 행을 잠가 막는다(아래 참고).
@Service
@RequiredArgsConstructor
@Transactional
public class FacilityConditionInquiryCommandService {

    // DenialReportCommandService.RATE_LIMIT_HOURS와 같은 값 — 같은 유저·시설 조합의 남용을 막는 창.
    private static final int RATE_LIMIT_HOURS = 24;

    private final FacilityConditionInquiryRepository facilityConditionInquiryRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public FacilityConditionInquiryResponseDTO.InquireResult inquire(
            Long userId,
            Long facilityId,
            String memo
    ) {
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(ErrorStatus.MEMBER4005);
        }
        // FK로만 쓰고 필드를 읽지 않아 전체 행을 가져올 필요가 없다 — getReferenceById로 프록시만 얻는다.
        User user = userRepository.getReferenceById(userId);

        // 대상 시설 행을 잠가 같은 시설에 대한 동시 요청을 직렬화한다. 잠금 없이 "최근 24시간
        // 내 요청했는지"를 확인한 뒤 저장하면, 거의 동시에 들어온 두 요청이 둘 다 그 확인을
        // 통과해 중복으로 쌓일 수 있다.
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        LocalDateTime rateLimitSince = LocalDateTime.now().minusHours(RATE_LIMIT_HOURS);
        boolean isAlreadyRequestedRecently = facilityConditionInquiryRepository
                .existsByUser_IdAndFacility_FacilityIdAndCreatedAtAfter(userId, facilityId, rateLimitSince);
        if (isAlreadyRequestedRecently) {
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
