package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.ConfidenceSource;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.petcheck.repository.FacilityPetCheckCount;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.repository.DowngradingDenialReport;
import com.freepets.domain.report.repository.FacilityDenialReportCount;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class OwnerFacilityQueryServiceTest {

    private static final long USER_ID = 1L;
    private static final long FACILITY_ID = 6L;
    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 9, 10, 9, 0);

    @Mock
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @Mock
    private FacilityReportRepository facilityReportRepository;

    @Mock
    private PetCheckRepository petCheckRepository;

    @InjectMocks
    private OwnerFacilityQueryService ownerFacilityQueryService;

    @Test
    void getMyFacilities_소유_매장이_없으면_빈_목록을_반환하고_집계를_조회하지_않는다() {
        when(facilityOwnerClaimRepository.findApprovedWithFacilityByUserId(USER_ID)).thenReturn(List.of());

        BusinessResponseDTO.OwnerFacilityList result = ownerFacilityQueryService.getMyFacilities(USER_ID);

        assertThat(result.facilities()).isEmpty();
        verify(facilityReportRepository, never()).countDowngradingByFacilityIds(anyList(), any());
        verify(petCheckRepository, never()).countByFacilityIdsSince(anyList(), any());
    }

    @Test
    void getMyFacilities_카드_지표를_함께_내려준다() {
        givenOwnedFacility(confirmedFacility());
        givenDenialReports(List.of(), List.of());
        givenWeeklyPetChecks(new FacilityPetCheckCount(FACILITY_ID, 3L));

        BusinessResponseDTO.OwnerFacility facility = firstFacility();

        assertThat(facility.facilityId()).isEqualTo(FACILITY_ID);
        assertThat(facility.name()).isEqualTo("테라로사 커피공장");
        assertThat(facility.entryCondition().petAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.stats().weeklyPetCheckCount()).isEqualTo(3L);
        assertThat(facility.stats().reviewCount()).isEqualTo(12L);
    }

    /** 확정했고 그 이후 제보가 없으면 확정 배지다. 손님이 보는 시설 상세와 같은 규칙이어야 한다. */
    @Test
    void getMyFacilities_확정_이후_제보가_없으면_확정_배지다() {
        givenOwnedFacility(confirmedFacility());
        givenDenialReports(List.of(), List.of());
        givenWeeklyPetChecks();

        BusinessResponseDTO.OwnerFacility facility = firstFacility();

        assertThat(facility.entryCondition().confidence()).isEqualTo(Confidence.CONFIRMED);
        assertThat(facility.entryCondition().confidenceSource()).isEqualTo(ConfidenceSource.OWNER);
        assertThat(facility.denialAlerts().count()).isZero();
        assertThat(facility.denialAlerts().latest()).isNull();
    }

    /** 확정 이후 제보가 들어오면 확정해뒀어도 배지가 내려간다. 경고 카드도 함께 채워진다. */
    @Test
    void getMyFacilities_확정_이후_제보가_있으면_배지가_내려가고_경고가_채워진다() {
        LocalDateTime reportedAt = LocalDateTime.of(2026, 9, 16, 13, 13);
        givenOwnedFacility(confirmedFacility());
        givenDenialReports(
                List.of(new FacilityDenialReportCount(FACILITY_ID, 3L)),
                List.of(new DowngradingDenialReport(FACILITY_ID, DenialReason.INDOOR, reportedAt))
        );
        givenWeeklyPetChecks();

        BusinessResponseDTO.OwnerFacility facility = firstFacility();

        assertThat(facility.entryCondition().confidence()).isEqualTo(Confidence.UNVERIFIED);
        assertThat(facility.entryCondition().confidenceSource()).isEqualTo(ConfidenceSource.DENIAL_REPORT);
        assertThat(facility.entryCondition().confirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(facility.denialAlerts().count()).isEqualTo(3L);
        assertThat(facility.denialAlerts().latest().reason()).isEqualTo(DenialReason.INDOOR);
        assertThat(facility.denialAlerts().latest().reportedAt()).isEqualTo(reportedAt);
    }

    /** 리뷰도 판별도 없는 신규 매장이 0으로 내려가야 한다 — 집계 결과에 아예 행이 없는 경우다. */
    @Test
    void getMyFacilities_집계_결과가_없는_매장은_0으로_채운다() {
        givenOwnedFacility(newFacility());
        givenDenialReports(List.of(), List.of());
        givenWeeklyPetChecks();

        BusinessResponseDTO.OwnerFacility facility = firstFacility();

        assertThat(facility.stats().weeklyPetCheckCount()).isZero();
        assertThat(facility.stats().reviewCount()).isZero();
        assertThat(facility.denialAlerts().count()).isZero();
        assertThat(facility.denialAlerts().latest()).isNull();
    }

    /**
     * 건수와 최신 제보는 서로 다른 쿼리에서 온다. 그 사이에 제보가 정리돼 한쪽만 비어도 경고 카드가
     * 깨지지 않아야 한다 — 건수를 기준으로 맞춘다.
     */
    @Test
    void getMyFacilities_건수만_있고_최신_제보가_없으면_경고를_그리지_않는다() {
        givenOwnedFacility(confirmedFacility());
        givenDenialReports(List.of(new FacilityDenialReportCount(FACILITY_ID, 2L)), List.of());
        givenWeeklyPetChecks();

        BusinessResponseDTO.OwnerFacility facility = firstFacility();

        assertThat(facility.denialAlerts().count()).isZero();
        assertThat(facility.denialAlerts().latest()).isNull();
    }

    /**
     * 이번 주는 롤링 7일이 아니라 달력 주다. 서버가 UTC이므로 KST 월요일 00:00을 UTC로 옮긴 값이
     * 넘어가야 한다 — UTC 기준으로 월요일을 잡으면 월요일 오전 9시(KST) 이전 판별이 지난 주로 밀린다.
     */
    @Test
    void getMyFacilities_이번_주_판별은_KST_월요일_00시부터_센다() {
        givenOwnedFacility(confirmedFacility());
        givenDenialReports(List.of(), List.of());
        givenWeeklyPetChecks();

        ownerFacilityQueryService.getMyFacilities(USER_ID);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(petCheckRepository).countByFacilityIdsSince(anyList(), since.capture());

        ZoneId businessZone = ZoneId.of("Asia/Seoul");
        LocalDateTime expected = LocalDate.now(businessZone)
                .with(DayOfWeek.MONDAY)
                .atStartOfDay(businessZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        assertThat(since.getValue()).isEqualTo(expected);
    }

    /** 건수와 최신 제보가 다른 기준선을 쓰면 둘이 어긋난다 — 한 번 구한 값을 두 쿼리에 함께 넘겨야 한다. */
    @Test
    void getMyFacilities_두_제보_조회는_같은_기준선을_쓴다() {
        givenOwnedFacility(confirmedFacility());
        givenDenialReports(List.of(), List.of());
        givenWeeklyPetChecks();

        ownerFacilityQueryService.getMyFacilities(USER_ID);

        ArgumentCaptor<LocalDateTime> countSince = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> latestSince = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(facilityReportRepository).countDowngradingByFacilityIds(anyList(), countSince.capture());
        verify(facilityReportRepository).findLatestDowngradingByFacilityIds(anyList(), latestSince.capture());

        assertThat(countSince.getValue()).isEqualTo(latestSince.getValue());
        assertThat(countSince.getValue()).isCloseTo(
                LocalDateTime.now().minusDays(FacilityReport.RECENT_WINDOW_DAYS),
                within(10, ChronoUnit.SECONDS)
        );
    }

    private BusinessResponseDTO.OwnerFacility firstFacility() {
        return ownerFacilityQueryService.getMyFacilities(USER_ID).facilities().get(0);
    }

    private void givenOwnedFacility(Facility facility) {
        when(facilityOwnerClaimRepository.findApprovedWithFacilityByUserId(USER_ID))
                .thenReturn(List.of(createClaim(facility)));
    }

    private void givenDenialReports(
            List<FacilityDenialReportCount> counts,
            List<DowngradingDenialReport> latest
    ) {
        when(facilityReportRepository.countDowngradingByFacilityIds(anyList(), any())).thenReturn(counts);
        when(facilityReportRepository.findLatestDowngradingByFacilityIds(anyList(), any())).thenReturn(latest);
    }

    private void givenWeeklyPetChecks(FacilityPetCheckCount... counts) {
        when(petCheckRepository.countByFacilityIdsSince(anyList(), any())).thenReturn(List.of(counts));
    }

    /** 사업자가 조건을 확정해둔 매장. 리뷰 12건은 등급 캐시 컬럼에 들어 있는 값이다. */
    private Facility confirmedFacility() {
        Facility facility = facility(PetAllowed.ALLOWED);
        ReflectionTestUtils.setField(facility, "confirmedAt", CONFIRMED_AT);
        ReflectionTestUtils.setField(facility, "reviewCount", 12L);
        return facility;
    }

    /** 막 등록돼 리뷰도 판별도 없는 매장. */
    private Facility newFacility() {
        return facility(PetAllowed.PENDING);
    }

    private Facility facility(PetAllowed petAllowed) {
        Facility facility = Facility.builder()
                .name("테라로사 커피공장")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 구정면 현천길 25")
                .lat(new BigDecimal("37.7000000"))
                .lng(new BigDecimal("128.8000000"))
                .petAllowed(petAllowed)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", FACILITY_ID);
        return facility;
    }

    private FacilityOwnerClaim createClaim(Facility facility) {
        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        FacilityOwnerClaim claim = FacilityOwnerClaim.builder()
                .user(user)
                .facility(facility)
                .maskedBusinessNumber("123-45-*****")
                .verifiedAt(CONFIRMED_AT)
                .build();
        ReflectionTestUtils.setField(claim, "status", ClaimStatus.APPROVED);
        return claim;
    }
}
