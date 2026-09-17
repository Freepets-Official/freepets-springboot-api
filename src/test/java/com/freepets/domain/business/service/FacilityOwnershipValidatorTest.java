package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class FacilityOwnershipValidatorTest {

    private static final long USER_ID = 1L;
    private static final long FACILITY_ID = 6L;

    @Mock
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @InjectMocks
    private FacilityOwnershipValidator facilityOwnershipValidator;

    @Test
    void requireOwner_승인된_소유_기록이_있으면_통과한다() {
        when(facilityOwnerClaimRepository.existsApprovedByFacilityIdAndUserId(FACILITY_ID, USER_ID))
                .thenReturn(true);

        assertThatCode(() -> facilityOwnershipValidator.requireOwner(USER_ID, FACILITY_ID))
                .doesNotThrowAnyException();
    }

    /**
     * 심사 중·반려·해제된 신청은 소유권이 아니다. 리포지토리 쿼리가 {@code APPROVED}만 세므로 이 셋은
     * 전부 "기록 없음"으로 돌아온다 — 상태별로 나눠 검증할 것은 쿼리 쪽이다.
     */
    @Test
    void requireOwner_승인된_소유_기록이_없으면_403으로_막는다() {
        when(facilityOwnerClaimRepository.existsApprovedByFacilityIdAndUserId(FACILITY_ID, USER_ID))
                .thenReturn(false);

        GeneralException exception = catchThrowableOfType(
                () -> facilityOwnershipValidator.requireOwner(USER_ID, FACILITY_ID),
                GeneralException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4008);
    }

    /** 없는 시설도 승인된 기록이 없으니 같은 403이다 — 비소유자에게 시설 존재를 알려주지 않는다. */
    @Test
    void requireOwner_없는_시설도_404가_아니라_403이다() {
        long missingFacilityId = 999L;
        when(facilityOwnerClaimRepository.existsApprovedByFacilityIdAndUserId(missingFacilityId, USER_ID))
                .thenReturn(false);

        GeneralException exception = catchThrowableOfType(
                () -> facilityOwnershipValidator.requireOwner(USER_ID, missingFacilityId),
                GeneralException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4008);
    }

    @Test
    void BUSINESS4008은_403을_쓴다() {
        assertThat(ErrorStatus.BUSINESS4008.getHttpStatus().value()).isEqualTo(403);
    }
}
