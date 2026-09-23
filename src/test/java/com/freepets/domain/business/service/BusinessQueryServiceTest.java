package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.geocoding.GeocodedAddress;
import com.freepets.infra.nts.NtsClient;
import com.freepets.infra.nts.NtsException;
import com.freepets.infra.nts.NtsProperties;
import com.freepets.infra.nts.NtsValidationResult;

@ExtendWith(MockitoExtension.class)
class BusinessQueryServiceTest {

    @Mock
    private NtsClient ntsClient;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private FacilityDuplicateCandidateQueryService facilityDuplicateCandidateQueryService;

    private BusinessQueryService businessQueryService;

    @BeforeEach
    void setUp() {
        businessQueryService = new BusinessQueryService(
                new NtsProperties("service-key"), ntsClient, geocodingService, facilityDuplicateCandidateQueryService
        );
    }

    private BusinessRequestDTO.VerifyRequest createRequest() {
        BusinessRequestDTO.VerifyRequest request = new BusinessRequestDTO.VerifyRequest();
        request.setBusinessNumber("1234567890");
        request.setRepresentativeName("홍길동");
        request.setOpeningDate("20200315");
        return request;
    }

    private void givenValidationResult(NtsValidationResult result) {
        when(ntsClient.validate("1234567890", "홍길동", "20200315")).thenReturn(result);
    }

    @Test
    void 계속사업자면_인증에_성공한다() {
        givenValidationResult(new NtsValidationResult(true, "", "01", "계속사업자"));

        BusinessResponseDTO.VerifyResult result = businessQueryService.verify(createRequest());

        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo("01");
        assertThat(result.statusLabel()).isEqualTo("계속사업자");
    }

    @Test
    void 국세청_정보와_일치하지_않으면_BUSINESS4001() {
        givenValidationResult(new NtsValidationResult(false, "확인할 수 없습니다.", "", ""));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessQueryService.verify(createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4001);
    }

    @Test
    void 휴업자면_BUSINESS4002와_함께_상태를_내려준다() {
        givenValidationResult(new NtsValidationResult(true, "", "02", "휴업자"));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessQueryService.verify(createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4002);
        // 화면이 휴업인지 폐업인지 보여줘야 해서 상태를 함께 싣는다.
        BusinessResponseDTO.VerifyResult result = (BusinessResponseDTO.VerifyResult) exception.getResult();
        assertThat(result.status()).isEqualTo("02");
        assertThat(result.statusLabel()).isEqualTo("휴업자");
    }

    @Test
    void 폐업자면_BUSINESS4002() {
        givenValidationResult(new NtsValidationResult(true, "", "03", "폐업자"));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessQueryService.verify(createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4002);
    }

    @Test
    void 국세청_호출에_실패하면_BUSINESS5001() {
        // 외부 API 실패는 502로 내린다. 사용자가 잘못한 게 아니라는 뜻이다.
        when(ntsClient.validate("1234567890", "홍길동", "20200315"))
                .thenThrow(new NtsException("국세청 API 호출에 실패했습니다."));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessQueryService.verify(createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS5001);
    }

    @Test
    void 서비스키가_없으면_호출하지_않고_BUSINESS5001() {
        // 키가 없으면 클라이언트 생성이 실패하는데, 그 예외는 스프링이 감싸서 던져 NtsException
        // 처리에 안 걸린다. 그대로 두면 서버 설정 문제가 사용자에게 500으로 나간다.
        BusinessQueryService serviceWithoutKey = new BusinessQueryService(
                new NtsProperties("  "), ntsClient, geocodingService, facilityDuplicateCandidateQueryService
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> serviceWithoutKey.verify(createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS5001);
        verifyNoInteractions(ntsClient);
    }

    @Test
    void duplicateCheck_주소를_지오코딩한_뒤_후보를_조회한다() {
        BusinessRequestDTO.FacilityDuplicateCheckRequest request = new BusinessRequestDTO.FacilityDuplicateCheckRequest();
        request.setName("카페 파도살롱");
        request.setAddress("강원 강릉시 창해로 17");

        GeocodedAddress geocoded = new GeocodedAddress(new BigDecimal("37.8"), new BigDecimal("128.9"));
        when(geocodingService.geocode("강원 강릉시 창해로 17")).thenReturn(geocoded);

        BusinessResponseDTO.FacilityDuplicateCandidate candidate = new BusinessResponseDTO.FacilityDuplicateCandidate(
                6L, "카페 파도살롱", "강원 강릉시 창해로 17", FacilityCategory.CAFE, FacilitySource.TOUR_API, 30.0
        );
        when(facilityDuplicateCandidateQueryService.findCandidates("카페 파도살롱", 37.8, 128.9))
                .thenReturn(List.of(candidate));

        BusinessResponseDTO.FacilityDuplicateCandidateList result = businessQueryService.duplicateCheck(request);

        assertThat(result.candidates()).containsExactly(candidate);
    }

    @Test
    void duplicateCheck_지오코딩에_실패하면_후보_조회를_하지_않는다() {
        BusinessRequestDTO.FacilityDuplicateCheckRequest request = new BusinessRequestDTO.FacilityDuplicateCheckRequest();
        request.setName("카페 파도살롱");
        request.setAddress("존재하지 않는 주소");

        when(geocodingService.geocode("존재하지 않는 주소"))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4011));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessQueryService.duplicateCheck(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4011);
        verifyNoInteractions(facilityDuplicateCandidateQueryService);
    }
}
