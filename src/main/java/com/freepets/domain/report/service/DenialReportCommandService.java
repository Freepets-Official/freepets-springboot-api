package com.freepets.domain.report.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.report.converter.DenialReportConverter;
import com.freepets.domain.report.dto.DenialReportResponseDTO;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.entity.ReportStatus;
import com.freepets.domain.report.entity.ReportType;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// POST /api/v1/facilities/{facilityId}/denial-reports — F4 원터치 실시간 거부 제보.
// 사진·서술형 설명 없이 사유 하나로 즉시 접수되고 검토 없이 바로 반영된다(status=APPLIED).
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DenialReportCommandService {

    // 같은 유저·시설 조합의 남용(같은 이유로 여러 번 눌러 신뢰도를 과하게 깎는 것)을 막는 창.
    //
    // 알려진 한계: exists 확인과 save가 한 트랜잭션 안에서도 원자적이지 않다 — 같은 유저가 같은
    // 시설에 정확히 동시에(예: 더블 탭, 재시도) 요청 2개를 보내면 둘 다 exists 확인을 통과한 뒤
    // 저장될 수 있다. (user_id, facility_id) 유니크 제약을 걸기엔 "24시간 롤링 윈도우"가 고정
    // 키가 아니라 안 맞고, 이 리포에 아직 없는 락 패턴을 이 기능 하나 때문에 새로 들이는 것도
    // 과하다고 판단해 지금은 막지 않는다 — 실제 피해도 제한적이다(신뢰도 하향은 제보 1건만
    // 있어도 이미 적용되는 이진 값이라 중복 제보로 더 나빠지지 않고, 3건↑ 로그도 단순 경고라
    // 중복이 섞여도 관리자가 직접 확인하는 절차는 그대로다).
    private static final int RATE_LIMIT_HOURS = 24;

    // 이 이상 쌓이면 관리자 확인이 필요하다고 본다. 이 리포엔 관리자 도메인/API가 아예 없어서
    // 지금은 로그만 남긴다 — 관리자 기능이 생기면 이 지점을 실제 승격 호출로 바꾸면 된다.
    private static final long ESCALATION_THRESHOLD = 3;

    private final FacilityReportRepository facilityReportRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public DenialReportResponseDTO.Report report(
            Long userId,
            Long facilityId,
            DenialReason reason
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        LocalDateTime rateLimitSince = LocalDateTime.now().minusHours(RATE_LIMIT_HOURS);
        boolean alreadyReportedRecently = facilityReportRepository
                .existsByUser_IdAndFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(userId, facilityId, rateLimitSince);
        if (alreadyReportedRecently) {
            throw new GeneralException(ErrorStatus.REPORT4001);
        }

        FacilityReport saved = facilityReportRepository.save(
                FacilityReport.builder()
                        .user(user)
                        .facility(facility)
                        .reportType(ReportType.DENIED)
                        .denialReason(reason)
                        .status(ReportStatus.APPLIED)
                        .isRealtime(true)
                        .build()
        );

        warnIfEscalationThresholdReached(facilityId);

        return DenialReportConverter.toReport(saved, userId);
    }

    private void warnIfEscalationThresholdReached(Long facilityId) {
        LocalDateTime escalationSince = LocalDateTime.now().minusDays(FacilityReport.RECENT_WINDOW_DAYS);
        long recentCount = facilityReportRepository
                .countByFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(facilityId, escalationSince);

        if (recentCount >= ESCALATION_THRESHOLD) {
            log.warn(
                    "시설(facilityId={})에 최근 {}일간 실시간 거부 제보가 {}건 쌓였습니다 — 관리자 확인이 필요할 수 있습니다.",
                    facilityId,
                    FacilityReport.RECENT_WINDOW_DAYS,
                    recentCount
            );
        }
    }
}
