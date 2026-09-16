package com.freepets.domain.business.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.petcheck.repository.FacilityPetCheckCount;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.repository.DowngradingDenialReport;
import com.freepets.domain.report.repository.FacilityDenialReportCount;
import com.freepets.domain.report.repository.FacilityReportRepository;

import lombok.RequiredArgsConstructor;

/**
 * 사업자 대시보드 조회.
 *
 * <p><b>시설 ID를 인자로 받는 메서드를 여기 추가한다면 {@link FacilityOwnershipValidator}를 먼저
 * 불러야 한다.</b> 아래 내 매장 목록은 조회 자체가 요청자의 소유 기록에서 출발해 남의 매장이 섞일 수
 * 없어 검증기를 부르지 않지만, 그건 이 메서드에 한정된 이야기다.
 *
 * <p>매장 등록 신청(심사 중·반려 이력) 조회는 {@link FacilityOwnerClaimQueryService}가 맡는다 —
 * 등록 이전 단계라 대시보드와 화면이 다르다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerFacilityQueryService {

    // 서버 프로세스는 항상 UTC로 고정돼 있다(FreepetsServerApplication의 static 블록) — "이번 주"의
    // 경계는 서버 타임존이 아니라 실제 사용자가 있는 KST 기준이어야 한다(GamificationService와 같다).
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;
    private final FacilityReportRepository facilityReportRepository;
    private final PetCheckRepository petCheckRepository;
    private final FacilityOwnershipValidator facilityOwnershipValidator;

    /**
     * 내 매장 목록. 대시보드 홈이 이 응답 하나로 매장 카드와 거부 제보 경고를 모두 그린다.
     *
     * <p>소유 매장이 없으면 빈 목록을 돌려준다 — 404가 아니다. 소유 매장이 0개가 되면 사업자 프로필
     * 자체가 사라지므로, 앱은 이 응답이 비는 것을 보고 소비자 화면으로 되돌린다.
     */
    public BusinessResponseDTO.OwnerFacilityList getMyFacilities(Long userId) {
        List<FacilityOwnerClaim> claims = facilityOwnerClaimRepository.findApprovedWithFacilityByUserId(userId);
        if (claims.isEmpty()) {
            return new BusinessResponseDTO.OwnerFacilityList(List.of());
        }

        List<Long> facilityIds = claims.stream()
                .map(claim -> claim.getFacility().getFacilityId())
                .toList();
        // 두 제보 조회는 같은 기준선을 써야 건수와 최신 제보가 어긋나지 않는다. 호출 사이에 자정이
        // 지나면 값이 갈리므로 한 번 구해서 둘 다에 넘긴다.
        LocalDateTime denialReportSince = denialReportSince();

        return BusinessConverter.toOwnerFacilityList(
                claims,
                countDenialAlerts(facilityIds, denialReportSince),
                findLatestDenialAlerts(facilityIds, denialReportSince),
                countWeeklyPetChecks(facilityIds)
        );
    }

    /**
     * 거부 제보 전체 조회. 홈의 경고 카드를 탭했을 때 들어가는 화면이라, 확정 이후 실시간 거부 제보를
     * 최신순으로 전부 보여준다. {@code countDenialAlerts}/{@code findLatestDenialAlerts}와 같은
     * 기준선({@link #denialReportSince()})을 써야 홈의 건수와 이 목록이 어긋나지 않는다.
     */
    public BusinessResponseDTO.DenialAlertList getDenialAlerts(Long userId, Long facilityId) {
        facilityOwnershipValidator.requireOwner(userId, facilityId);

        List<FacilityReport> reports = facilityReportRepository.findDowngradingByFacilityId(
                facilityId,
                denialReportSince()
        );
        return BusinessConverter.toDenialAlertList(reports);
    }

    private Map<Long, Long> countDenialAlerts(
            List<Long> facilityIds,
            LocalDateTime since
    ) {
        return facilityReportRepository.countDowngradingByFacilityIds(facilityIds, since).stream()
                .collect(Collectors.toMap(
                        FacilityDenialReportCount::facilityId,
                        FacilityDenialReportCount::reportCount
                ));
    }

    private Map<Long, DowngradingDenialReport> findLatestDenialAlerts(
            List<Long> facilityIds,
            LocalDateTime since
    ) {
        return facilityReportRepository.findLatestDowngradingByFacilityIds(facilityIds, since).stream()
                .collect(Collectors.toMap(
                        DowngradingDenialReport::facilityId,
                        Function.identity(),
                        // 한 시설에 제보 시각이 완전히 같은 두 건이 있으면 두 행이 온다. 어느 쪽을 보여줘도
                        // 같은 시각이라 먼저 온 것을 쓴다 — 병합 함수가 없으면 여기서 예외가 난다.
                        (first, second) -> first
                ));
    }

    private Map<Long, Long> countWeeklyPetChecks(List<Long> facilityIds) {
        return petCheckRepository.countByFacilityIdsSince(facilityIds, weekStart()).stream()
                .collect(Collectors.toMap(
                        FacilityPetCheckCount::facilityId,
                        FacilityPetCheckCount::checkCount
                ));
    }

    /**
     * 거부 제보를 어디까지 거슬러 볼지. 시설별로 확정 시각을 한 번 더 거르는 건 쿼리가 맡고, 여기서는
     * 공통 하한만 준다({@code FacilityReportRepository}의 두 쿼리 주석 참고).
     *
     * <p>{@link #weekStart()}와 달리 타임존을 변환하지 않는다. 롤링 7일에는 달력 경계가 없어 어느
     * 타임존에서 세든 같은 구간이고, 저장된 {@code createdAt}이 UTC라 {@code LocalDateTime.now()}가
     * 그대로 맞는 값이다. 기존 {@code FacilityQueryService.denialReportSince}도 같은 방식이다.
     */
    private static LocalDateTime denialReportSince() {
        return LocalDateTime.now().minusDays(FacilityReport.RECENT_WINDOW_DAYS);
    }

    /**
     * 이번 주의 시작 — KST 월요일 00:00을 UTC로 옮긴 값이다. 화면 문구가 "이번 주"라 최근 7일이 아니라
     * 달력 주를 쓴다. 달력 경계를 쓰므로 위와 달리 타임존 변환이 필요하다 — UTC로 월요일을 구하면 월요일
     * 오전 9시 이전(KST)의 판별이 지난 주로 밀린다.
     *
     * <p>{@code LocalDate.with(DayOfWeek.MONDAY)}는 ISO 주(월~일) 기준이라 일요일에 불러도 그 주
     * 월요일로 간다.
     */
    private static LocalDateTime weekStart() {
        return LocalDate.now(BUSINESS_ZONE)
                .with(DayOfWeek.MONDAY)
                .atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
