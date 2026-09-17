package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class FacilitySelfRegistrationCommandServiceTest {

    private static final long USER_ID = 1L;
    private static final String MASKED_BUSINESS_NUMBER = "123-45-*****";
    private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 9, 17, 10, 0);

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FacilitySelfRegistrationCommandService facilitySelfRegistrationCommandService;

    private User createUser() {
        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private Region createRegion() {
        return Region.builder()
                .sidoCode("32")
                .sido("강원특별자치도")
                .sigunguCode("32210")
                .sigungu("강릉시")
                .build();
    }

    private BusinessRequestDTO.FacilityRegisterRequest createRequest() {
        BusinessRequestDTO.FacilityRegisterRequest request = new BusinessRequestDTO.FacilityRegisterRequest();
        request.setBusinessNumber("1234567890");
        request.setRepresentativeName("홍길동");
        request.setOpeningDate("20200315");
        request.setName("새로 연 카페");
        request.setCategory(FacilityCategory.CAFE);
        request.setAddress("강원 강릉시 창해로 20");
        request.setSidoCode("32");
        request.setSigunguCode("32210");
        request.setPetAllowed(PetAllowed.ALLOWED);
        request.setRequirements(List.of(Requirement.LEASH));
        request.setConditionRaw("리드줄 착용 시 동반 가능");
        return request;
    }

    @Test
    void validateRegion_존재하는_코드면_해당_지역을_반환한다() {
        Region region = createRegion();
        when(regionRepository.findBySidoCodeAndSigunguCode("32", "32210")).thenReturn(Optional.of(region));

        Region found = facilitySelfRegistrationCommandService.validateRegion("32", "32210");

        assertThat(found).isEqualTo(region);
    }

    @Test
    void validateRegion_존재하지_않는_코드면_BUSINESS4012() {
        when(regionRepository.findBySidoCodeAndSigunguCode("99", "99999")).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilitySelfRegistrationCommandService.validateRegion("99", "99999")
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4012);
    }

    @Test
    void register_관광공사_출처가_아닌_자체등록_시설을_만들고_즉시_승인된_소유권을_저장한다() {
        User user = createUser();
        Region region = createRegion();
        BusinessRequestDTO.FacilityRegisterRequest request = createRequest();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(facilityRepository.save(any(Facility.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(facilityOwnerClaimRepository.save(any(FacilityOwnerClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessResponseDTO.FacilityRegisterResult result = facilitySelfRegistrationCommandService.register(
                USER_ID, request, region,
                new BigDecimal("37.8000000"), new BigDecimal("128.9000000"),
                MASKED_BUSINESS_NUMBER, VERIFIED_AT
        );

        assertThat(result.name()).isEqualTo("새로 연 카페");
        assertThat(result.category()).isEqualTo(FacilityCategory.CAFE);
        assertThat(result.status()).isEqualTo(ClaimStatus.APPROVED);

        org.mockito.ArgumentCaptor<Facility> facilityCaptor = org.mockito.ArgumentCaptor.forClass(Facility.class);
        org.mockito.Mockito.verify(facilityRepository).save(facilityCaptor.capture());
        Facility savedFacility = facilityCaptor.getValue();
        assertThat(savedFacility.getContentId()).isNull();
        assertThat(savedFacility.getSource()).isEqualTo(FacilitySource.BUSINESS_SELF);
        assertThat(savedFacility.isPetTourListed()).isFalse();
        assertThat(savedFacility.isActive()).isTrue();
        assertThat(savedFacility.getSidoCode()).isEqualTo("32");
        assertThat(savedFacility.getSigunguCode()).isEqualTo("32210");
        // 법정동 명칭 정본은 Region이다 — 요청 값이 아니라 region에서 가져온다.
        assertThat(savedFacility.getSido()).isEqualTo(region.getSido());
        assertThat(savedFacility.getSigungu()).isEqualTo(region.getSigungu());

        // confirmByOwner 호출로 체크리스트와 confirmedAt이 세팅돼 신뢰도가 CONFIRMED/OWNER로 올라간다.
        assertThat(savedFacility.getConfirmedAt()).isNotNull();
        assertThat(savedFacility.getCheckLists()).extracting("type").containsExactly(Requirement.LEASH);

        org.mockito.ArgumentCaptor<FacilityOwnerClaim> claimCaptor =
                org.mockito.ArgumentCaptor.forClass(FacilityOwnerClaim.class);
        org.mockito.Mockito.verify(facilityOwnerClaimRepository).save(claimCaptor.capture());
        FacilityOwnerClaim savedClaim = claimCaptor.getValue();
        assertThat(savedClaim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(savedClaim.getMaskedBusinessNumber()).isEqualTo(MASKED_BUSINESS_NUMBER);
        assertThat(savedClaim.getVerifiedAt()).isEqualTo(VERIFIED_AT);
    }

    @Test
    void register_존재하지_않는_유저면_MEMBER4005() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilitySelfRegistrationCommandService.register(
                        USER_ID, createRequest(), createRegion(),
                        new BigDecimal("37.8"), new BigDecimal("128.9"),
                        MASKED_BUSINESS_NUMBER, VERIFIED_AT
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
    }
}
