package com.freepets.domain.business.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.event.FacilityBecameIneligibleEvent;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.repository.FacilityDenialReportCount;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * 사업자의 출입 조건 직접 수정. 재심사({@code PENDING}) 없이 즉시 반영한다 — 이미 등록증 대조를 통과한
 * 소유자이므로, 재심사로 돌리면 그 사이 시설이 틀린 옛 조건을 계속 노출하게 된다.
 *
 * <p>승인({@link FacilityOwnerClaimAdminCommandService#approve})과 같은 {@link Facility#confirmByOwner}를
 * 타므로, {@code confirmedAt} 갱신 제한(판별값이 실제로 바뀐 경우에만 갱신)과 추천 자격 상실 이벤트 발행도
 * 그 메서드가 함께 보장한다.
 *
 * <p>시설 ID를 인자로 받으므로 {@link FacilityOwnershipValidator}를 가장 먼저 호출한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerFacilityConditionCommandService {

    private final FacilityOwnershipValidator facilityOwnershipValidator;
    private final FacilityRepository facilityRepository;
    private final FacilityReportRepository facilityReportRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BusinessResponseDTO.EntryCondition updateConditions(
            Long userId,
            Long facilityId,
            BusinessRequestDTO.ConditionUpdateRequest request
    ) {
        facilityOwnershipValidator.requireOwner(userId, facilityId);

        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        boolean wasEligible = facility.isEligibleForRecommendation();
        facility.confirmByOwner(
                request.getPetAllowed(),
                request.getMaxWeight(),
                request.getMaxWeightInclusive(),
                request.getRequirements(),
                request.getConditionRaw()
        );
        if (wasEligible && !facility.isEligibleForRecommendation()) {
            eventPublisher.publishEvent(new FacilityBecameIneligibleEvent(facility.getFacilityId()));
        }

        return BusinessConverter.toEntryCondition(facility, countDenialAlert(facilityId));
    }

    /**
     * 홈 목록({@link OwnerFacilityQueryService})과 같은 공식을 쓴다 — 확정 이후 제보만 세는 필터링은
     * {@code countDowngradingByFacilityIds} 쿼리 안에서 {@code facility.confirmedAt}을 직접 조인해
     * 처리하므로, 여기서는 평평한 하한(최근 {@code RECENT_WINDOW_DAYS}일)만 넘기면 된다.
     *
     * <p>이 메서드는 반드시 {@link Facility#confirmByOwner} 호출 <b>이후</b>에 불려야 한다. 방금 바뀐
     * {@code confirmedAt}이 아직 flush되지 않은 상태로 이 쿼리가 나가도, JPA가 겹치는 테이블(facilities)에
     * 대한 변경을 감지해 쿼리 전에 자동 flush하므로 최신 값을 본다 — 이 자동 flush에 기대고 있다는 점을
     * 남겨둔다(트랜잭션 flush 모드를 바꾸면 이 가정이 깨질 수 있다).
     */
    private long countDenialAlert(Long facilityId) {
        LocalDateTime since = LocalDateTime.now().minusDays(FacilityReport.RECENT_WINDOW_DAYS);
        return facilityReportRepository.countDowngradingByFacilityIds(List.of(facilityId), since).stream()
                .findFirst()
                .map(FacilityDenialReportCount::reportCount)
                .orElse(0L);
    }
}
