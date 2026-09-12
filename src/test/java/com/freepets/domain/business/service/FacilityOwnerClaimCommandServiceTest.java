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
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.ConfidenceSource;
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

    private FacilityOwnerClaim createClaim(
            User user,
            Facility facility
    ) {
        return FacilityOwnerClaim.builder()
                .user(user)
                .facility(facility)
                .maskedBusinessNumber(MASKED_BUSINESS_NUMBER)
                .verifiedAt(VERIFIED_AT)
                .build();
    }

    private BusinessResponseDTO.ClaimResult claim() {
        return facilityOwnerClaimCommandService.claim(
                OWNER_ID, FACILITY_ID, MASKED_BUSINESS_NUMBER, VERIFIED_AT, createRequest()
        );
    }

    @Test
    void 주인이_없는_매장이면_소유_기록을_만들고_조건을_확정한다() {
        Facility facility = createFacility();
        User owner = createUser(OWNER_ID);

        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findByFacility_FacilityId(FACILITY_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

        BusinessResponseDTO.ClaimResult result = claim();

        assertThat(result.facilityId()).isEqualTo(FACILITY_ID);
        assertThat(result.confidence()).isEqualTo(Confidence.CONFIRMED);
        assertThat(result.confidenceSource()).isEqualTo(ConfidenceSource.OWNER);
        assertThat(result.confirmedAt()).isNotNull();

        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.getPetConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        // 같은 조건이 여러 번 와도 체크리스트에 중복으로 쌓이지 않는다.
        assertThat(facility.getCheckLists()).hasSize(1);

        ArgumentCaptor<FacilityOwnerClaim> claimCaptor = ArgumentCaptor.forClass(FacilityOwnerClaim.class);
        verify(facilityOwnerClaimRepository).save(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getMaskedBusinessNumber()).isEqualTo(MASKED_BUSINESS_NUMBER);
        assertThat(claimCaptor.getValue().getVerifiedAt()).isEqualTo(VERIFIED_AT);
        assertThat(claimCaptor.getValue().getUser()).isEqualTo(owner);
    }

    @Test
    void 다른_사업자가_이미_등록한_매장이면_BUSINESS4003() {
        Facility facility = createFacility();

        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findByFacility_FacilityId(FACILITY_ID))
                .thenReturn(Optional.of(createClaim(createUser(OTHER_USER_ID), facility)));

        GeneralException exception = assertThrows(GeneralException.class, this::claim);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
        // 남의 매장 조건을 건드리면 안 된다.
        assertThat(facility.getConfirmedAt()).isNull();
        verify(facilityOwnerClaimRepository, never()).save(any());
    }

    @Test
    void 본인이_이미_등록한_매장이면_조건만_갱신한다() {
        // 뒤로 갔다 다시 제출하거나 네트워크 재시도로 같은 요청이 두 번 와도 에러를 보여줄 이유가 없다.
        Facility facility = createFacility();

        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findByFacility_FacilityId(FACILITY_ID))
                .thenReturn(Optional.of(createClaim(createUser(OWNER_ID), facility)));

        BusinessResponseDTO.ClaimResult result = claim();

        assertThat(result.confidence()).isEqualTo(Confidence.CONFIRMED);
        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        verify(facilityOwnerClaimRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY4001() {
        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class, this::claim);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4001);
        verifyNoInteractions(facilityOwnerClaimRepository, userRepository);
    }

    @Test
    void 유니크_제약에_걸리면_BUSINESS4003() {
        // 행 잠금을 타지 않는 경로가 생겨도 DB 제약이 마지막 방어선으로 남는지 고정한다.
        Facility facility = createFacility();

        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findByFacility_FacilityId(FACILITY_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(createUser(OWNER_ID)));
        when(facilityOwnerClaimRepository.save(any(FacilityOwnerClaim.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key", new ConstraintViolationException(
                        "duplicate key value violates unique constraint",
                        new SQLException("duplicate key"),
                        "uk_facility_owner_claims_facility_id"
                )));

        GeneralException exception = assertThrows(GeneralException.class, this::claim);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
    }

    @Test
    void 다른_원인의_무결성_위반이면_변환하지_않고_그대로_던진다() {
        // FK·not-null 위반까지 409로 뭉뚱그리면 진짜 원인이 가려진다.
        Facility facility = createFacility();
        DataIntegrityViolationException otherViolation = new DataIntegrityViolationException(
                "null value in column", new ConstraintViolationException(
                        "not-null constraint", new SQLException("not null"), "some_other_constraint"
                )
        );

        when(facilityRepository.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(facilityOwnerClaimRepository.findByFacility_FacilityId(FACILITY_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(createUser(OWNER_ID)));
        when(facilityOwnerClaimRepository.save(any(FacilityOwnerClaim.class))).thenThrow(otherViolation);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                this::claim
        );

        assertThat(exception).isSameAs(otherViolation);
    }
}
