package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class FacilityOwnerClaimQueryServiceTest {

    private static final long USER_ID = 1L;
    private static final long CLAIM_ID = 11L;
    private static final long FACILITY_ID = 6L;
    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 9, 16, 10, 0);

    @Mock
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @InjectMocks
    private FacilityOwnerClaimQueryService facilityOwnerClaimQueryService;

    private Facility createFacility() {
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
        ReflectionTestUtils.setField(facility, "facilityId", FACILITY_ID);
        return facility;
    }

    private FacilityOwnerClaim createClaim(ClaimStatus status) {
        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        FacilityOwnerClaim claim = FacilityOwnerClaim.builder()
                .user(user)
                .facility(createFacility())
                .maskedBusinessNumber("123-45-*****")
                .verifiedAt(APPLIED_AT)
                .build();
        ReflectionTestUtils.setField(claim, "claimId", CLAIM_ID);
        ReflectionTestUtils.setField(claim, "status", status);
        ReflectionTestUtils.setField(claim, "createdAt", APPLIED_AT);
        return claim;
    }

    @Test
    void getMyClaims_레포지토리_결과를_그대로_변환해_반환한다() {
        when(facilityOwnerClaimRepository.findAllWithFacilityByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(createClaim(ClaimStatus.PENDING)));

        BusinessResponseDTO.MyClaimList result = facilityOwnerClaimQueryService.getMyClaims(USER_ID);

        assertThat(result.claims()).hasSize(1);
        BusinessResponseDTO.MyClaim myClaim = result.claims().get(0);
        assertThat(myClaim.claimId()).isEqualTo(CLAIM_ID);
        assertThat(myClaim.facilityId()).isEqualTo(FACILITY_ID);
        assertThat(myClaim.facilityName()).isEqualTo("카페 파도살롱");
        assertThat(myClaim.facilityAddress()).isEqualTo("강원 강릉시 창해로 17");
        assertThat(myClaim.status()).isEqualTo(ClaimStatus.PENDING);
        assertThat(myClaim.appliedAt()).isEqualTo(APPLIED_AT);
    }

    @Test
    void getMyClaims_신청이_없으면_빈_목록을_반환한다() {
        when(facilityOwnerClaimRepository.findAllWithFacilityByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());

        BusinessResponseDTO.MyClaimList result = facilityOwnerClaimQueryService.getMyClaims(USER_ID);

        assertThat(result.claims()).isEmpty();
    }
}
