package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityBenefit;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityBenefitRepository;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class OwnerFacilityBenefitCommandServiceTest {

    private static final long USER_ID = 1L;
    private static final long FACILITY_ID = 6L;
    private static final long BENEFIT_ID = 10L;

    @Mock
    private FacilityOwnershipValidator facilityOwnershipValidator;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityBenefitRepository facilityBenefitRepository;

    @InjectMocks
    private OwnerFacilityBenefitCommandService ownerFacilityBenefitCommandService;

    private Facility createFacility() {
        Facility facility = Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", FACILITY_ID);
        return facility;
    }

    private FacilityBenefit createBenefit(Facility facility) {
        FacilityBenefit benefit = FacilityBenefit.builder()
                .facility(facility)
                .title("출입증 제시 시 음료 10% 할인")
                .description("프리펫츠 동반 출입증을 보여주세요")
                .build();
        ReflectionTestUtils.setField(benefit, "facilityBenefitId", BENEFIT_ID);
        return benefit;
    }

    @Test
    void createBenefit_비소유자면_BUSINESS4008이고_시설을_건드리지_않는다() {
        doThrow(new GeneralException(ErrorStatus.BUSINESS4008))
                .when(facilityOwnershipValidator).requireOwner(USER_ID, FACILITY_ID);
        BusinessRequestDTO.VisitBenefitCreateRequest request = new BusinessRequestDTO.VisitBenefitCreateRequest();
        request.setTitle("출입증 제시 시 음료 10% 할인");

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityBenefitCommandService.createBenefit(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4008);
        verifyNoInteractions(facilityRepository, facilityBenefitRepository);
    }

    @Test
    void createBenefit_존재하지_않는_시설이면_FACILITY4001() {
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.empty());
        BusinessRequestDTO.VisitBenefitCreateRequest request = new BusinessRequestDTO.VisitBenefitCreateRequest();
        request.setTitle("출입증 제시 시 음료 10% 할인");

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityBenefitCommandService.createBenefit(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4001);
    }

    @Test
    void createBenefit_정상_등록하면_켜진_상태로_저장된다() {
        Facility facility = createFacility();
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));
        BusinessRequestDTO.VisitBenefitCreateRequest request = new BusinessRequestDTO.VisitBenefitCreateRequest();
        request.setTitle("출입증 제시 시 음료 10% 할인");
        request.setDescription("프리펫츠 동반 출입증을 보여주세요");

        BusinessResponseDTO.VisitBenefit result =
                ownerFacilityBenefitCommandService.createBenefit(USER_ID, FACILITY_ID, request);

        assertThat(result.title()).isEqualTo("출입증 제시 시 음료 10% 할인");
        assertThat(result.description()).isEqualTo("프리펫츠 동반 출입증을 보여주세요");
        assertThat(result.isEnabled()).isTrue();
        verify(facilityBenefitRepository).save(org.mockito.ArgumentMatchers.any(FacilityBenefit.class));
    }

    @Test
    void deleteBenefit_비소유자면_BUSINESS4008이고_아무것도_건드리지_않는다() {
        doThrow(new GeneralException(ErrorStatus.BUSINESS4008))
                .when(facilityOwnershipValidator).requireOwner(USER_ID, FACILITY_ID);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityBenefitCommandService.deleteBenefit(USER_ID, FACILITY_ID, BENEFIT_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4008);
        verifyNoInteractions(facilityBenefitRepository);
    }

    @Test
    void deleteBenefit_존재하지_않거나_다른_시설의_혜택이면_BUSINESS4009() {
        when(facilityBenefitRepository.findByFacilityBenefitIdAndFacility_FacilityId(BENEFIT_ID, FACILITY_ID))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityBenefitCommandService.deleteBenefit(USER_ID, FACILITY_ID, BENEFIT_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4009);
    }

    @Test
    void deleteBenefit_정상_삭제하면_삭제된_ID를_돌려준다() {
        Facility facility = createFacility();
        FacilityBenefit benefit = createBenefit(facility);
        when(facilityBenefitRepository.findByFacilityBenefitIdAndFacility_FacilityId(BENEFIT_ID, FACILITY_ID))
                .thenReturn(Optional.of(benefit));

        BusinessResponseDTO.VisitBenefitDeleteResult result =
                ownerFacilityBenefitCommandService.deleteBenefit(USER_ID, FACILITY_ID, BENEFIT_ID);

        assertThat(result.benefitId()).isEqualTo(BENEFIT_ID);
        verify(facilityBenefitRepository).delete(benefit);
    }

    @Test
    void updateEnabled_존재하지_않으면_BUSINESS4009() {
        when(facilityBenefitRepository.findByFacilityBenefitIdAndFacility_FacilityId(BENEFIT_ID, FACILITY_ID))
                .thenReturn(Optional.empty());
        BusinessRequestDTO.VisitBenefitEnabledUpdateRequest request =
                new BusinessRequestDTO.VisitBenefitEnabledUpdateRequest();
        request.setIsEnabled(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityBenefitCommandService.updateEnabled(USER_ID, FACILITY_ID, BENEFIT_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4009);
    }

    @Test
    void updateEnabled_끄면_isEnabled가_false다() {
        Facility facility = createFacility();
        FacilityBenefit benefit = createBenefit(facility);
        when(facilityBenefitRepository.findByFacilityBenefitIdAndFacility_FacilityId(BENEFIT_ID, FACILITY_ID))
                .thenReturn(Optional.of(benefit));
        BusinessRequestDTO.VisitBenefitEnabledUpdateRequest request =
                new BusinessRequestDTO.VisitBenefitEnabledUpdateRequest();
        request.setIsEnabled(false);

        BusinessResponseDTO.VisitBenefit result =
                ownerFacilityBenefitCommandService.updateEnabled(USER_ID, FACILITY_ID, BENEFIT_ID, request);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void updateEnabled_다시_켜면_isEnabled가_true다() {
        Facility facility = createFacility();
        FacilityBenefit benefit = createBenefit(facility);
        benefit.disable();
        when(facilityBenefitRepository.findByFacilityBenefitIdAndFacility_FacilityId(BENEFIT_ID, FACILITY_ID))
                .thenReturn(Optional.of(benefit));
        BusinessRequestDTO.VisitBenefitEnabledUpdateRequest request =
                new BusinessRequestDTO.VisitBenefitEnabledUpdateRequest();
        request.setIsEnabled(true);

        BusinessResponseDTO.VisitBenefit result =
                ownerFacilityBenefitCommandService.updateEnabled(USER_ID, FACILITY_ID, BENEFIT_ID, request);

        assertThat(result.isEnabled()).isTrue();
    }
}
