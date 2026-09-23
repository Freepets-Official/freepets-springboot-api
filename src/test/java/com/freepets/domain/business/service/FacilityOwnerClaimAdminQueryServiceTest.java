package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.entity.RequestedCondition;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class FacilityOwnerClaimAdminQueryServiceTest {

    private static final long CLAIM_ID = 11L;
    private static final long FACILITY_ID = 6L;
    private static final long OTHER_FACILITY_ID = 7L;
    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 9, 16, 10, 0);

    @Mock
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @InjectMocks
    private FacilityOwnerClaimAdminQueryService facilityOwnerClaimAdminQueryService;

    private Facility createFacility(long facilityId) {
        Facility facility = Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.PENDING)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

    private FacilityOwnerClaim createClaim(long facilityId) {
        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        FacilityOwnerClaim claim = FacilityOwnerClaim.builder()
                .user(user)
                .facility(createFacility(facilityId))
                .maskedBusinessNumber("123-45-*****")
                .verifiedAt(APPLIED_AT)
                .requestedCondition(RequestedCondition.of(
                        PetAllowed.ALLOWED, new BigDecimal("10.00"), true,
                        List.of(Requirement.LEASH), "리드줄 착용 시 실내 동반 가능"
                ))
                .registrationCertificateUrl("https://bucket.s3.ap-northeast-2.amazonaws.com/certificate.pdf")
                .build();
        ReflectionTestUtils.setField(claim, "claimId", CLAIM_ID);
        ReflectionTestUtils.setField(claim, "createdAt", APPLIED_AT);
        return claim;
    }

    /**
     * 4단계(신청 접수) 이전에 곧바로 승인 상태로 만들어진 기존 매장을 흉내낸다. requestedCondition을
     * 저장한 적이 없어 null이다.
     */
    private FacilityOwnerClaim createLegacyApprovedClaim() {
        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        FacilityOwnerClaim claim = FacilityOwnerClaim.builder()
                .user(user)
                .facility(createFacility(FACILITY_ID))
                .maskedBusinessNumber("123-45-*****")
                .verifiedAt(APPLIED_AT)
                .build();
        ReflectionTestUtils.setField(claim, "claimId", CLAIM_ID);
        ReflectionTestUtils.setField(claim, "createdAt", APPLIED_AT);
        ReflectionTestUtils.setField(claim, "status", ClaimStatus.APPROVED);
        return claim;
    }

    @Test
    void getClaims_신청_조건을_저장한_적_없는_기존_승인_매장도_예외_없이_변환한다() {
        FacilityOwnerClaim claim = createLegacyApprovedClaim();
        when(facilityOwnerClaimRepository.findByStatus(eq(ClaimStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(List.of(claim), PageRequest.of(0, 20), 1));
        when(facilityOwnerClaimRepository.findApprovedFacilityIdsIn(List.of(FACILITY_ID)))
                .thenReturn(List.of(FACILITY_ID));

        BusinessResponseDTO.AdminClaimList result =
                facilityOwnerClaimAdminQueryService.getClaims(ClaimStatus.APPROVED, 0, 20);

        BusinessResponseDTO.AdminClaim adminClaim = result.claims().get(0);
        assertThat(adminClaim.requestedPetAllowed()).isNull();
        assertThat(adminClaim.requestedMaxWeight()).isNull();
        assertThat(adminClaim.requestedMaxWeightInclusive()).isNull();
        assertThat(adminClaim.requestedRequirements()).isEmpty();
        assertThat(adminClaim.requestedConditionRaw()).isNull();
    }

    @Test
    void getClaims_승인된_소유자가_있는_시설의_신청은_hasApprovedOwner가_true다() {
        FacilityOwnerClaim claim = createClaim(FACILITY_ID);
        when(facilityOwnerClaimRepository.findByStatus(eq(ClaimStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(claim), PageRequest.of(0, 20), 1));
        when(facilityOwnerClaimRepository.findApprovedFacilityIdsIn(List.of(FACILITY_ID)))
                .thenReturn(List.of(FACILITY_ID));

        BusinessResponseDTO.AdminClaimList result =
                facilityOwnerClaimAdminQueryService.getClaims(ClaimStatus.PENDING, 0, 20);

        assertThat(result.claims()).hasSize(1);
        BusinessResponseDTO.AdminClaim adminClaim = result.claims().get(0);
        assertThat(adminClaim.claimId()).isEqualTo(CLAIM_ID);
        assertThat(adminClaim.facilityId()).isEqualTo(FACILITY_ID);
        assertThat(adminClaim.applicantNickname()).isEqualTo("사장님");
        assertThat(adminClaim.requestedPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(adminClaim.requestedRequirements()).containsExactly(Requirement.LEASH);
        assertThat(adminClaim.registrationCertificateUrl())
                .isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/certificate.pdf");
        assertThat(adminClaim.hasApprovedOwner()).isTrue();
        assertThat(result.pageInfo().totalElements()).isEqualTo(1);
    }

    @Test
    void getClaims_승인된_소유자가_없는_시설의_신청은_hasApprovedOwner가_false다() {
        FacilityOwnerClaim claim = createClaim(OTHER_FACILITY_ID);
        when(facilityOwnerClaimRepository.findByStatus(eq(ClaimStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(claim), PageRequest.of(0, 20), 1));
        when(facilityOwnerClaimRepository.findApprovedFacilityIdsIn(List.of(OTHER_FACILITY_ID)))
                .thenReturn(List.of());

        BusinessResponseDTO.AdminClaimList result =
                facilityOwnerClaimAdminQueryService.getClaims(ClaimStatus.PENDING, 0, 20);

        assertThat(result.claims().get(0).hasApprovedOwner()).isFalse();
    }

    @Test
    void getClaims_결과가_없으면_승인_여부_조회를_건너뛴다() {
        when(facilityOwnerClaimRepository.findByStatus(eq(ClaimStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        BusinessResponseDTO.AdminClaimList result =
                facilityOwnerClaimAdminQueryService.getClaims(ClaimStatus.PENDING, 0, 20);

        assertThat(result.claims()).isEmpty();
        verify(facilityOwnerClaimRepository, never()).findApprovedFacilityIdsIn(any());
    }

    @Test
    void getClaims_페이지_크기가_상한을_넘으면_잘라낸다() {
        when(facilityOwnerClaimRepository.findByStatus(eq(ClaimStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        facilityOwnerClaimAdminQueryService.getClaims(ClaimStatus.PENDING, 0, 999);

        verify(facilityOwnerClaimRepository).findByStatus(eq(ClaimStatus.PENDING), eq(PageRequest.of(0, 50)));
    }
}
