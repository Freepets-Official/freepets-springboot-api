package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.freepets.domain.facility.event.FacilityBecameIneligibleEvent;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class FacilityOwnerClaimAdminCommandServiceTest {

    private static final long CLAIM_ID = 11L;
    private static final long FACILITY_ID = 6L;
    private static final long ADMIN_USER_ID = 99L;

    @Mock
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FacilityOwnerClaimAdminCommandService facilityOwnerClaimAdminCommandService;

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

    private FacilityOwnerClaim createClaim(
            ClaimStatus status,
            Facility facility
    ) {
        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();

        FacilityOwnerClaim claim = FacilityOwnerClaim.builder()
                .user(user)
                .facility(facility)
                .maskedBusinessNumber("123-45-*****")
                .verifiedAt(LocalDateTime.of(2026, 9, 12, 10, 0))
                .requestedCondition(RequestedCondition.of(
                        PetAllowed.ALLOWED, new BigDecimal("10.00"), true,
                        List.of(Requirement.LEASH), "리드줄 착용 시 실내 동반 가능"
                ))
                .registrationCertificateUrl("https://bucket.s3.ap-northeast-2.amazonaws.com/certificate.pdf")
                .build();
        ReflectionTestUtils.setField(claim, "claimId", CLAIM_ID);
        ReflectionTestUtils.setField(claim, "status", status);
        return claim;
    }

    // ---------------------------------------------------------------
    // approve
    // ---------------------------------------------------------------

    @Test
    void approve_대기_신청을_승인하면_시설에_조건을_반영하고_상태를_바꾼다() {
        Facility facility = createFacility(PetAllowed.PENDING);
        FacilityOwnerClaim claim = createClaim(ClaimStatus.PENDING, facility);

        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findApprovedByFacilityId(FACILITY_ID)).thenReturn(Optional.empty());

        BusinessResponseDTO.AdminClaimActionResult result =
                facilityOwnerClaimAdminCommandService.approve(ADMIN_USER_ID, CLAIM_ID);

        assertThat(result.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(facility.getConfirmedAt()).isNotNull();
        // 동반 가능(ALLOWED)으로 확정돼도 추천 후보 자격은 그대로라 이벤트가 없어야 한다.
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void approve_동반_불가로_확정돼_추천_자격을_잃으면_이벤트를_발행한다() {
        Facility facility = createFacility(PetAllowed.PENDING);
        FacilityOwnerClaim claim = createClaim(ClaimStatus.PENDING, facility);
        ReflectionTestUtils.setField(
                claim, "requestedCondition",
                RequestedCondition.of(PetAllowed.DENIED, null, null, List.of(), "동반 불가")
        );

        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findApprovedByFacilityId(FACILITY_ID)).thenReturn(Optional.empty());

        facilityOwnerClaimAdminCommandService.approve(ADMIN_USER_ID, CLAIM_ID);

        assertThat(facility.isEligibleForRecommendation()).isFalse();
        verify(eventPublisher).publishEvent(new FacilityBecameIneligibleEvent(FACILITY_ID));
    }

    @Test
    void approve_존재하지_않는_신청이면_BUSINESS4006() {
        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimAdminCommandService.approve(ADMIN_USER_ID, CLAIM_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4006);
        verifyNoInteractions(facilityRepository, eventPublisher);
    }

    @Test
    void approve_PENDING이_아닌_신청이면_BUSINESS4007이고_시설을_건드리지_않는다() {
        FacilityOwnerClaim claim = createClaim(ClaimStatus.REJECTED, createFacility(PetAllowed.PENDING));
        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimAdminCommandService.approve(ADMIN_USER_ID, CLAIM_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4007);
        verifyNoInteractions(facilityRepository, eventPublisher);
    }

    @Test
    void approve_이미_승인된_소유자가_있으면_BUSINESS4003이고_시설을_건드리지_않는다() {
        // 같은 매장의 다른 대기 신청을 자동 반려하지 않기로 했다 — 관리자가 먼저 반려해야 한다.
        Facility facility = createFacility(PetAllowed.PENDING);
        FacilityOwnerClaim claim = createClaim(ClaimStatus.PENDING, facility);
        FacilityOwnerClaim existingApproved = createClaim(ClaimStatus.APPROVED, facility);

        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findApprovedByFacilityId(FACILITY_ID))
                .thenReturn(Optional.of(existingApproved));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimAdminCommandService.approve(ADMIN_USER_ID, CLAIM_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
        assertThat(facility.getConfirmedAt()).isNull();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void approve_시설이_존재하지_않으면_FACILITY4001() {
        FacilityOwnerClaim claim = createClaim(ClaimStatus.PENDING, createFacility(PetAllowed.PENDING));
        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimAdminCommandService.approve(ADMIN_USER_ID, CLAIM_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4001);
    }

    // ---------------------------------------------------------------
    // reject
    // ---------------------------------------------------------------

    @Test
    void reject_대기_신청을_반려하면_상태와_사유를_남기고_시설은_건드리지_않는다() {
        FacilityOwnerClaim claim = createClaim(ClaimStatus.PENDING, createFacility(PetAllowed.PENDING));
        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));

        BusinessResponseDTO.AdminClaimActionResult result =
                facilityOwnerClaimAdminCommandService.reject(ADMIN_USER_ID, CLAIM_ID, "등록증 상호 불일치");

        assertThat(result.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getReviewReason()).isEqualTo("등록증 상호 불일치");
        assertThat(claim.getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
        verifyNoInteractions(facilityRepository, eventPublisher);
    }

    @Test
    void reject_PENDING이_아닌_신청이면_BUSINESS4007() {
        FacilityOwnerClaim claim = createClaim(ClaimStatus.APPROVED, createFacility(PetAllowed.PENDING));
        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimAdminCommandService.reject(ADMIN_USER_ID, CLAIM_ID, "사유")
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4007);
    }

    @Test
    void reject_존재하지_않는_신청이면_BUSINESS4006() {
        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimAdminCommandService.reject(ADMIN_USER_ID, CLAIM_ID, "사유")
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4006);
    }

    // ---------------------------------------------------------------
    // revoke
    // ---------------------------------------------------------------

    @Test
    void revoke_승인된_신청을_해제하면_시설의_확정도_함께_풀린다() {
        Facility facility = createFacility(PetAllowed.PENDING);
        facility.confirmByOwner(PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH), "동반 가능");
        FacilityOwnerClaim claim = createClaim(ClaimStatus.APPROVED, facility);

        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));

        BusinessResponseDTO.AdminClaimActionResult result =
                facilityOwnerClaimAdminCommandService.revoke(ADMIN_USER_ID, CLAIM_ID, "이의 제기로 소유권 회수");

        assertThat(result.status()).isEqualTo(ClaimStatus.REVOKED);
        assertThat(claim.getReviewReason()).isEqualTo("이의 제기로 소유권 회수");
        assertThat(facility.getConfirmedAt()).isNull();
        // 확정만 풀 뿐, 동기화 전까지 조건 값 자체는 남아 있다.
        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
    }

    @Test
    void revoke_APPROVED가_아닌_신청이면_BUSINESS4007이고_시설을_건드리지_않는다() {
        FacilityOwnerClaim claim = createClaim(ClaimStatus.PENDING, createFacility(PetAllowed.PENDING));
        when(facilityOwnerClaimRepository.findByIdForUpdate(CLAIM_ID)).thenReturn(Optional.of(claim));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimAdminCommandService.revoke(ADMIN_USER_ID, CLAIM_ID, "사유")
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4007);
        verifyNoInteractions(facilityRepository);
    }
}
