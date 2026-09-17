package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
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
import com.freepets.domain.facility.entity.FacilityAmenity;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class OwnerFacilityProfileCommandServiceTest {

    private static final long USER_ID = 1L;
    private static final long FACILITY_ID = 6L;

    @Mock
    private FacilityOwnershipValidator facilityOwnershipValidator;

    @Mock
    private FacilityRepository facilityRepository;

    @InjectMocks
    private OwnerFacilityProfileCommandService ownerFacilityProfileCommandService;

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

    private BusinessRequestDTO.FacilityProfileUpdateRequest createRequest(
            String introduction,
            List<FacilityAmenity> amenityTags
    ) {
        BusinessRequestDTO.FacilityProfileUpdateRequest request =
                new BusinessRequestDTO.FacilityProfileUpdateRequest();
        request.setIntroduction(introduction);
        request.setAmenityTags(amenityTags);
        return request;
    }

    @Test
    void 비소유자면_BUSINESS4008이고_시설을_건드리지_않는다() {
        doThrow(new GeneralException(ErrorStatus.BUSINESS4008))
                .when(facilityOwnershipValidator).requireOwner(USER_ID, FACILITY_ID);
        BusinessRequestDTO.FacilityProfileUpdateRequest request =
                createRequest("소개글", List.of(FacilityAmenity.PARKING));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityProfileCommandService.updateProfile(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4008);
        verifyNoInteractions(facilityRepository);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY4001() {
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.empty());
        BusinessRequestDTO.FacilityProfileUpdateRequest request =
                createRequest("소개글", List.of());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> ownerFacilityProfileCommandService.updateProfile(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4001);
    }

    @Test
    void 정상_저장하면_요청값이_응답에_그대로_담긴다() {
        Facility facility = createFacility();
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));
        BusinessRequestDTO.FacilityProfileUpdateRequest request = createRequest(
                "대형견도 환영해요. 야외 테라스와 급수대, 펫 메뉴가 준비돼 있어요.",
                List.of(FacilityAmenity.WATER_BOWL, FacilityAmenity.OUTDOOR_TERRACE, FacilityAmenity.PET_MENU)
        );

        BusinessResponseDTO.FacilityProfile result =
                ownerFacilityProfileCommandService.updateProfile(USER_ID, FACILITY_ID, request);

        assertThat(result.introduction())
                .isEqualTo("대형견도 환영해요. 야외 테라스와 급수대, 펫 메뉴가 준비돼 있어요.");
        assertThat(result.amenityTags()).containsExactly(
                FacilityAmenity.WATER_BOWL, FacilityAmenity.OUTDOOR_TERRACE, FacilityAmenity.PET_MENU
        );
        verify(facilityOwnershipValidator).requireOwner(USER_ID, FACILITY_ID);
    }

    @Test
    void 빈_태그_목록으로_저장하면_전부_해제된다() {
        Facility facility = createFacility();
        facility.updateProfile("이전 소개글", List.of(FacilityAmenity.PARKING));
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));
        BusinessRequestDTO.FacilityProfileUpdateRequest request = createRequest("새 소개글", List.of());

        BusinessResponseDTO.FacilityProfile result =
                ownerFacilityProfileCommandService.updateProfile(USER_ID, FACILITY_ID, request);

        assertThat(result.amenityTags()).isEmpty();
    }
}
