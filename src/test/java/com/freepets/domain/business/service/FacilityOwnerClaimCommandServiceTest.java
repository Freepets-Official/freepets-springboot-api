package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class FacilityOwnerClaimCommandServiceTest {

    private static final long FACILITY_ID = 6L;
    private static final long OWNER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final String MASKED_BUSINESS_NUMBER = "123-45-*****";
    private static final String CERTIFICATE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/certificate.pdf";
    private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 9, 12, 14, 0);

    @Mock
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FacilityOwnerClaimCommandService facilityOwnerClaimCommandService;

    private User createUser(long userId) {
        User user = User.builder()
                .email("owner" + userId + "@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

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

    private BusinessRequestDTO.ClaimRequest createRequest() {
        BusinessRequestDTO.ClaimRequest request = new BusinessRequestDTO.ClaimRequest();
        request.setBusinessNumber("1234567890");
        request.setRepresentativeName("홍길동");
        request.setOpeningDate("20200315");
        request.setPetAllowed(PetAllowed.ALLOWED);
        request.setMaxWeight(new BigDecimal("10.00"));
        request.setMaxWeightInclusive(true);
        request.setRequirements(List.of(Requirement.LEASH, Requirement.LEASH));
        request.setConditionRaw("리드줄 착용 시 실내 동반 가능");
        return request;
    }

    private FacilityOwnerClaim createApprovedClaim(
            User user,
            Facility facility
    ) {
        FacilityOwnerClaim claim = FacilityOwnerClaim.builder()
                .user(user)
                .facility(facility)
                .maskedBusinessNumber(MASKED_BUSINESS_NUMBER)
                .verifiedAt(VERIFIED_AT)
                .build();
        ReflectionTestUtils.setField(claim, "status", ClaimStatus.APPROVED);
        return claim;
    }

    private BusinessResponseDTO.ClaimResult apply() {
        return facilityOwnerClaimCommandService.apply(
                OWNER_ID, FACILITY_ID, MASKED_BUSINESS_NUMBER, VERIFIED_AT, CERTIFICATE_URL, createRequest()
        );
    }

    private void givenLockedFacility(Facility facility) {
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
    }

    private void givenNoApprovedOwner() {
        when(facilityOwnerClaimRepository.findApprovedByFacilityId(FACILITY_ID)).thenReturn(Optional.empty());
    }

    @Test
    void 신청하면_대기_기록에_조건과_등록증을_담고_시설은_건드리지_않는다() {
        Facility facility = createFacility();
        User owner = createUser(OWNER_ID);

        givenLockedFacility(facility);
        givenNoApprovedOwner();
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(facilityOwnerClaimRepository.save(any(FacilityOwnerClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessResponseDTO.ClaimResult result = apply();

        assertThat(result.facilityId()).isEqualTo(FACILITY_ID);
        assertThat(result.status()).isEqualTo(ClaimStatus.PENDING);

        // 승인 전에는 시설 정보가 바뀌면 안 된다 — 승인 절차를 둔 이유가 사라진다.
        assertThat(facility.getConfirmedAt()).isNull();
        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.PENDING);
        assertThat(facility.getCheckLists()).isEmpty();

        ArgumentCaptor<FacilityOwnerClaim> claimCaptor = ArgumentCaptor.forClass(FacilityOwnerClaim.class);
        verify(facilityOwnerClaimRepository).save(claimCaptor.capture());
        FacilityOwnerClaim savedClaim = claimCaptor.getValue();
        assertThat(savedClaim.getStatus()).isEqualTo(ClaimStatus.PENDING);
        assertThat(savedClaim.getMaskedBusinessNumber()).isEqualTo(MASKED_BUSINESS_NUMBER);
        assertThat(savedClaim.getVerifiedAt()).isEqualTo(VERIFIED_AT);
        assertThat(savedClaim.getUser()).isEqualTo(owner);
        assertThat(savedClaim.getRegistrationCertificateUrl()).isEqualTo(CERTIFICATE_URL);
        assertThat(savedClaim.getRequestedCondition().getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(savedClaim.getRequestedCondition().getMaxWeight()).isEqualTo(new BigDecimal("10.00"));
        assertThat(savedClaim.getRequestedCondition().getMaxWeightInclusive()).isTrue();
        assertThat(savedClaim.getRequestedCondition().getConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        // 같은 조건이 여러 번 와도 승인 시 체크리스트에 중복으로 쌓이지 않게 걸러둔다.
        assertThat(savedClaim.getRequestedCondition().getRequirements()).containsExactly(Requirement.LEASH);
    }

    @Test
    void 남이_이미_승인받은_매장이면_BUSINESS4003() {
        Facility facility = createFacility();

        givenLockedFacility(facility);
        when(facilityOwnerClaimRepository.findApprovedByFacilityId(FACILITY_ID))
                .thenReturn(Optional.of(createApprovedClaim(createUser(OTHER_USER_ID), facility)));

        GeneralException exception = assertThrows(GeneralException.class, this::apply);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
        verify(facilityOwnerClaimRepository, never()).save(any());
    }

    @Test
    void 내가_이미_등록을_마친_매장이면_BUSINESS4005() {
        // 신청할 이유가 없다. 승인된 매장의 조건 수정은 사업자 대시보드의 조건 수정 API가 맡는다.
        Facility facility = createFacility();

        givenLockedFacility(facility);
        when(facilityOwnerClaimRepository.findApprovedByFacilityId(FACILITY_ID))
                .thenReturn(Optional.of(createApprovedClaim(createUser(OWNER_ID), facility)));

        GeneralException exception = assertThrows(GeneralException.class, this::apply);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4005);
        assertThat(facility.getConfirmedAt()).isNull();
        verify(facilityOwnerClaimRepository, never()).save(any());
    }

    @Test
    void 본인의_대기_신청이_이미_있으면_BUSINESS4004() {
        givenLockedFacility(createFacility());
        givenNoApprovedOwner();
        when(facilityOwnerClaimRepository.existsPendingByFacilityIdAndUserId(FACILITY_ID, OWNER_ID))
                .thenReturn(true);

        GeneralException exception = assertThrows(GeneralException.class, this::apply);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4004);
        verify(facilityOwnerClaimRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY4001() {
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class, this::apply);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4001);
        verifyNoInteractions(facilityOwnerClaimRepository, userRepository);
    }

    @Test
    void validateApplicable_신청할_수_있으면_통과한다() {
        when(facilityRepository.existsById(FACILITY_ID)).thenReturn(true);
        givenNoApprovedOwner();
        when(facilityOwnerClaimRepository.existsPendingByFacilityIdAndUserId(FACILITY_ID, OWNER_ID))
                .thenReturn(false);

        facilityOwnerClaimCommandService.validateApplicable(OWNER_ID, FACILITY_ID);

        // 사전 확인은 아무것도 저장하지 않는다.
        verify(facilityOwnerClaimRepository, never()).save(any());
    }

    @Test
    void validateApplicable_승인된_주인이_있으면_apply와_같은_코드로_막는다() {
        Facility facility = createFacility();

        when(facilityRepository.existsById(FACILITY_ID)).thenReturn(true);
        when(facilityOwnerClaimRepository.findApprovedByFacilityId(FACILITY_ID))
                .thenReturn(Optional.of(createApprovedClaim(createUser(OTHER_USER_ID), facility)));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimCommandService.validateApplicable(OWNER_ID, FACILITY_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
    }

    @Test
    void validateApplicable_존재하지_않는_시설이면_FACILITY4001() {
        when(facilityRepository.existsById(FACILITY_ID)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityOwnerClaimCommandService.validateApplicable(OWNER_ID, FACILITY_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4001);
        verifyNoInteractions(facilityOwnerClaimRepository);
    }

    /** 주인이 없는 매장에 신청하다 저장 단계에서 주어진 무결성 위반이 나는 상황을 만든다. */
    private void givenSaveFailsWith(DataIntegrityViolationException violation) {
        givenLockedFacility(createFacility());
        givenNoApprovedOwner();
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(createUser(OWNER_ID)));
        when(facilityOwnerClaimRepository.save(any(FacilityOwnerClaim.class))).thenThrow(violation);
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException("duplicate key", new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("duplicate key"),
                constraintName
        ));
    }

    @Test
    void 승인된_소유자_유니크_인덱스에_걸리면_BUSINESS4003() {
        // 행 잠금을 타지 않는 경로가 생겨도 DB 제약이 마지막 방어선으로 남는지 고정한다.
        givenSaveFailsWith(constraintViolation("uk_facility_owner_claims_approved_facility"));

        GeneralException exception = assertThrows(GeneralException.class, this::apply);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
    }

    @Test
    void 대기_신청_유니크_인덱스에_걸리면_BUSINESS4004() {
        givenSaveFailsWith(constraintViolation("uk_facility_owner_claims_pending_user_facility"));

        GeneralException exception = assertThrows(GeneralException.class, this::apply);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4004);
    }

    @Test
    void 마이그레이션_전_옛_유니크_제약에_걸려도_BUSINESS4003() {
        // 수동 마이그레이션 전까지 DB에는 옛 "시설당 한 행" 제약이 남아 그 이름으로 충돌이 올라온다.
        givenSaveFailsWith(constraintViolation("uk_facility_owner_claims_facility_id"));

        GeneralException exception = assertThrows(GeneralException.class, this::apply);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
    }

    @Test
    void 다른_원인의_무결성_위반이면_변환하지_않고_그대로_던진다() {
        // FK·not-null 위반까지 409로 뭉뚱그리면 진짜 원인이 가려진다.
        DataIntegrityViolationException otherViolation = new DataIntegrityViolationException(
                "null value in column", new ConstraintViolationException(
                        "not-null constraint", new SQLException("not null"), "some_other_constraint"
                )
        );
        givenSaveFailsWith(otherViolation);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                this::apply
        );

        assertThat(exception).isSameAs(otherViolation);
    }

    @Test
    void 제약_이름을_알_수_없는_무결성_위반이면_변환하지_않고_그대로_던진다() {
        // 드라이버가 제약 이름을 못 알려주는 경우에도 판정 중에 터지지 않고 원래 예외를 올려야 한다.
        DataIntegrityViolationException unnamedViolation = constraintViolation(null);
        givenSaveFailsWith(unnamedViolation);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                this::apply
        );

        assertThat(exception).isSameAs(unnamedViolation);
    }
}
