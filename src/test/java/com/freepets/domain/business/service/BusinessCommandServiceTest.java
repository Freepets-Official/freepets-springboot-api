package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.ConfidenceSource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class BusinessCommandServiceTest {

    private static final long USER_ID = 1L;
    private static final long FACILITY_ID = 6L;

    @Mock
    private BusinessQueryService businessQueryService;

    @Mock
    private FacilityOwnerClaimCommandService facilityOwnerClaimCommandService;

    @InjectMocks
    private BusinessCommandService businessCommandService;

    private BusinessRequestDTO.ClaimRequest createRequest() {
        BusinessRequestDTO.ClaimRequest request = new BusinessRequestDTO.ClaimRequest();
        request.setBusinessNumber("1234567890");
        request.setRepresentativeName("홍길동");
        request.setOpeningDate("20200315");
        request.setPetAllowed(PetAllowed.ALLOWED);
        request.setRequirements(List.of(Requirement.LEASH));
        request.setConditionRaw("리드줄 착용 시 동반 가능");
        return request;
    }

    @Test
    void 사업자_확인을_통과하면_마스킹한_번호로_저장에_위임한다() {
        BusinessRequestDTO.ClaimRequest request = createRequest();

        when(businessQueryService.verify("1234567890", "홍길동", "20200315"))
                .thenReturn(new BusinessResponseDTO.VerifyResult(true, "01", "계속사업자"));
        when(facilityOwnerClaimCommandService.claim(
                eq(USER_ID), eq(FACILITY_ID), any(), any(), eq(request)
        )).thenReturn(new BusinessResponseDTO.ClaimResult(
                FACILITY_ID, Confidence.CONFIRMED, ConfidenceSource.OWNER, LocalDateTime.now()
        ));

        BusinessResponseDTO.ClaimResult result = businessCommandService.claim(USER_ID, FACILITY_ID, request);

        assertThat(result.confidence()).isEqualTo(Confidence.CONFIRMED);

        // 원본 번호는 저장 계층으로 넘어가지 않는다.
        ArgumentCaptor<String> maskedCaptor = ArgumentCaptor.forClass(String.class);
        verify(facilityOwnerClaimCommandService).claim(
                eq(USER_ID), eq(FACILITY_ID), maskedCaptor.capture(), any(LocalDateTime.class), eq(request)
        );
        assertThat(maskedCaptor.getValue()).isEqualTo("123-45-*****");
    }

    @Test
    void 사업자_확인에_실패하면_저장하지_않는다() {
        // 국세청 확인이 등록의 관문이다. 여기서 막히면 소유 기록이 생기면 안 된다.
        BusinessRequestDTO.ClaimRequest request = createRequest();

        when(businessQueryService.verify("1234567890", "홍길동", "20200315"))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4001));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.claim(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4001);
        verifyNoInteractions(facilityOwnerClaimCommandService);
    }

    @Test
    void 휴업_폐업이면_저장하지_않는다() {
        BusinessRequestDTO.ClaimRequest request = createRequest();

        when(businessQueryService.verify("1234567890", "홍길동", "20200315"))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4002));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.claim(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4002);
        verifyNoInteractions(facilityOwnerClaimCommandService);
    }

    @Test
    void 국세청_통신에_실패하면_저장하지_않는다() {
        BusinessRequestDTO.ClaimRequest request = createRequest();
        request.setMaxWeight(new BigDecimal("10.00"));

        when(businessQueryService.verify("1234567890", "홍길동", "20200315"))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS5001));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.claim(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS5001);
        verifyNoInteractions(facilityOwnerClaimCommandService);
    }
}
