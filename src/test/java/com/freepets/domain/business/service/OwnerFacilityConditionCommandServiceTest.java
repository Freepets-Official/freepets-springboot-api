package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.event.FacilityBecameIneligibleEvent;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.report.repository.FacilityDenialReportCount;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class OwnerFacilityConditionCommandServiceTest {

    private static final long USER_ID = 1L;
    private static final long FACILITY_ID = 6L;

    @Mock
    private FacilityOwnershipValidator facilityOwnershipValidator;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityReportRepository facilityReportRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OwnerFacilityConditionCommandService ownerFacilityConditionCommandService;

    private Facility createFacility(PetAllowed petAllowed) {
        Facility facility = Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(petAllowed)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", FACILITY_ID);
        return facility;
    }

    private BusinessRequestDTO.ConditionUpdateRequest createRequest(
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            Boolean maxWeightInclusive,
            List<Requirement> requirements,
            String conditionRaw
    ) {
        BusinessRequestDTO.ConditionUpdateRequest request = new BusinessRequestDTO.ConditionUpdateRequest();
        request.setPetAllowed(petAllowed);
        request.setMaxWeight(maxWeight);
        request.setMaxWeightInclusive(maxWeightInclusive);
        request.setRequirements(requirements);
        request.setConditionRaw(conditionRaw);
        return request;
    }

    @Test
    void 비소유자면_BUSINESS4008이고_시설과_이벤트를_건드리지_않는다() {
        doThrow(new GeneralException(ErrorStatus.BUSINESS4008))
                .when(facilityOwnershipValidator).requireOwner(USER_ID, FACILITY_ID);
        BusinessRequestDTO.ConditionUpdateRequest request = createRequest(
                PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH), "동반 가능"
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityConditionCommandService.updateConditions(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4008);
        verifyNoInteractions(facilityRepository, facilityReportRepository, eventPublisher);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY4001() {
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.empty());
        BusinessRequestDTO.ConditionUpdateRequest request = createRequest(
                PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH), "동반 가능"
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityConditionCommandService.updateConditions(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4001);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 정상_반영하면_요청값이_응답에_그대로_담긴다() {
        Facility facility = createFacility(PetAllowed.PENDING);
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityReportRepository.countDowngradingByFacilityIds(anyList(), any()))
                .thenReturn(List.of());
        BusinessRequestDTO.ConditionUpdateRequest request = createRequest(
                PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH), "리드줄 착용 시 실내 동반 가능"
        );

        BusinessResponseDTO.EntryCondition result =
                ownerFacilityConditionCommandService.updateConditions(USER_ID, FACILITY_ID, request);

        assertThat(result.petAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(result.maxWeight()).isEqualByComparingTo("10.00");
        assertThat(result.requirements()).containsExactly(Requirement.LEASH);
        assertThat(result.conditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        assertThat(result.confirmedAt()).isNotNull();
        assertThat(result.confidence()).isEqualTo(Confidence.CONFIRMED);
        verify(facilityOwnershipValidator).requireOwner(USER_ID, FACILITY_ID);
    }

    @Test
    void 동반_불가로_바뀌어_추천_자격을_잃으면_이벤트를_발행한다() {
        Facility facility = createFacility(PetAllowed.ALLOWED);
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityReportRepository.countDowngradingByFacilityIds(anyList(), any()))
                .thenReturn(List.of());
        BusinessRequestDTO.ConditionUpdateRequest request = createRequest(
                PetAllowed.DENIED, null, null, List.of(), "동반 불가로 전환"
        );

        ownerFacilityConditionCommandService.updateConditions(USER_ID, FACILITY_ID, request);

        assertThat(facility.isEligibleForRecommendation()).isFalse();
        verify(eventPublisher).publishEvent(new FacilityBecameIneligibleEvent(FACILITY_ID));
    }

    @Test
    void 이미_동반_불가인_시설을_다시_동반_불가로_보내면_이벤트를_발행하지_않는다() {
        Facility facility = createFacility(PetAllowed.DENIED);
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityReportRepository.countDowngradingByFacilityIds(anyList(), any()))
                .thenReturn(List.of());
        BusinessRequestDTO.ConditionUpdateRequest request = createRequest(
                PetAllowed.DENIED, null, null, List.of(), "여전히 동반 불가"
        );

        ownerFacilityConditionCommandService.updateConditions(USER_ID, FACILITY_ID, request);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 값이_바뀌지_않은_채_확정_이후_거부_제보가_있으면_신뢰도가_그대로_내려가_있다() {
        // 값을 바꾸지 않았으므로 confirmedAt이 갱신되지 않아, 그 이전부터 쌓인 거부 제보가 여전히 잡혀야 한다.
        Facility facility = createFacility(PetAllowed.PENDING);
        facility.confirmByOwner(
                PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH), "동반 가능"
        );
        LocalDateTime confirmedAtBeforeUpdate = facility.getConfirmedAt();
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityReportRepository.countDowngradingByFacilityIds(anyList(), any()))
                .thenReturn(List.of(new FacilityDenialReportCount(FACILITY_ID, 1L)));
        BusinessRequestDTO.ConditionUpdateRequest request = createRequest(
                PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH), "동반 가능"
        );

        BusinessResponseDTO.EntryCondition result =
                ownerFacilityConditionCommandService.updateConditions(USER_ID, FACILITY_ID, request);

        assertThat(facility.getConfirmedAt()).isEqualTo(confirmedAtBeforeUpdate);
        assertThat(result.confidence()).isEqualTo(Confidence.UNVERIFIED);
    }

    @Test
    void 값이_실제로_바뀌면_확정_시각이_갱신되어_신뢰도가_회복된다() {
        Facility facility = createFacility(PetAllowed.PENDING);
        facility.confirmByOwner(
                PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH), "동반 가능"
        );
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        // countDowngradingByFacilityIds 쿼리는 confirmedAt 이후 제보만 세므로, 갱신된 confirmedAt
        // 이전 제보는 실제로는 결과에서 빠진다 — 여기서는 그 결과(빈 목록)를 그대로 스텁한다.
        when(facilityReportRepository.countDowngradingByFacilityIds(anyList(), any()))
                .thenReturn(List.of());
        BusinessRequestDTO.ConditionUpdateRequest request = createRequest(
                PetAllowed.ALLOWED, new BigDecimal("12.00"), true, List.of(Requirement.LEASH), "동반 가능"
        );

        BusinessResponseDTO.EntryCondition result =
                ownerFacilityConditionCommandService.updateConditions(USER_ID, FACILITY_ID, request);

        assertThat(result.confidence()).isEqualTo(Confidence.CONFIRMED);
        verify(facilityReportRepository).countDowngradingByFacilityIds(anyList(), any());
    }
}
