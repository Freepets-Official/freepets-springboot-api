package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.geocoding.GeocodedAddress;
import com.freepets.infra.s3.S3ImageService;

@ExtendWith(MockitoExtension.class)
class BusinessCommandServiceTest {

    private static final long USER_ID = 1L;
    private static final long FACILITY_ID = 6L;
    private static final long CLAIM_ID = 11L;
    private static final String CERTIFICATE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/certificate.pdf";

    @Mock
    private BusinessQueryService businessQueryService;

    @Mock
    private FacilityOwnerClaimCommandService facilityOwnerClaimCommandService;

    @Mock
    private FacilitySelfRegistrationCommandService facilitySelfRegistrationCommandService;

    @Mock
    private FacilityDuplicateCandidateQueryService facilityDuplicateCandidateQueryService;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private S3ImageService s3ImageService;

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
        request.setRegistrationCertificate(
                new MockMultipartFile("registrationCertificate", "등록증.pdf", "application/pdf", new byte[] {1})
        );
        return request;
    }

    private void givenVerified() {
        when(businessQueryService.verify("1234567890", "홍길동", "20200315"))
                .thenReturn(new BusinessResponseDTO.VerifyResult(true, "01", "계속사업자"));
    }

    @Test
    void 사업자_확인을_통과하면_등록증을_올리고_마스킹한_번호로_신청_접수에_위임한다() {
        BusinessRequestDTO.ClaimRequest request = createRequest();

        givenVerified();
        when(s3ImageService.uploadDocument(request.getRegistrationCertificate())).thenReturn(CERTIFICATE_URL);
        when(facilityOwnerClaimCommandService.apply(
                eq(USER_ID), eq(FACILITY_ID), any(), any(), eq(CERTIFICATE_URL), eq(request)
        )).thenReturn(new BusinessResponseDTO.ClaimResult(CLAIM_ID, FACILITY_ID, ClaimStatus.PENDING));

        BusinessResponseDTO.ClaimResult result = businessCommandService.claim(USER_ID, FACILITY_ID, request);

        assertThat(result.claimId()).isEqualTo(CLAIM_ID);
        assertThat(result.status()).isEqualTo(ClaimStatus.PENDING);

        // 원본 번호는 저장 계층으로 넘어가지 않는다.
        ArgumentCaptor<String> maskedCaptor = ArgumentCaptor.forClass(String.class);
        verify(facilityOwnerClaimCommandService).apply(
                eq(USER_ID),
                eq(FACILITY_ID),
                maskedCaptor.capture(),
                any(LocalDateTime.class),
                eq(CERTIFICATE_URL),
                eq(request)
        );
        assertThat(maskedCaptor.getValue()).isEqualTo("123-45-*****");

        // 어차피 막힐 신청에 국세청 호출과 업로드를 쓰지 않도록 사전 확인이 먼저다.
        InOrder inOrder = inOrder(facilityOwnerClaimCommandService, businessQueryService, s3ImageService);
        inOrder.verify(facilityOwnerClaimCommandService).validateApplicable(USER_ID, FACILITY_ID);
        inOrder.verify(businessQueryService).verify("1234567890", "홍길동", "20200315");
        inOrder.verify(s3ImageService).uploadDocument(any());
        inOrder.verify(facilityOwnerClaimCommandService).apply(any(), any(), any(), any(), any(), any());
        verify(s3ImageService, never()).delete(any());
    }

    @Test
    void 신청할_수_없는_매장이면_국세청도_부르지_않고_업로드도_하지_않는다() {
        BusinessRequestDTO.ClaimRequest request = createRequest();

        doThrowOnValidate(ErrorStatus.BUSINESS4003);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.claim(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4003);
        verifyNoInteractions(businessQueryService, s3ImageService);
        verify(facilityOwnerClaimCommandService, never()).apply(any(), any(), any(), any(), any(), any());
    }

    private void doThrowOnValidate(ErrorStatus errorStatus) {
        doThrow(new GeneralException(errorStatus))
                .when(facilityOwnerClaimCommandService)
                .validateApplicable(USER_ID, FACILITY_ID);
    }

    @Test
    void 사업자_확인에_실패하면_업로드도_저장도_하지_않는다() {
        // 국세청 확인이 신청의 관문이다. 여기서 막히면 등록증도 올리면 안 된다.
        BusinessRequestDTO.ClaimRequest request = createRequest();

        when(businessQueryService.verify("1234567890", "홍길동", "20200315"))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4001));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.claim(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4001);
        verifyNoInteractions(s3ImageService);
        verify(facilityOwnerClaimCommandService, never()).apply(any(), any(), any(), any(), any(), any());
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
        verify(facilityOwnerClaimCommandService, never()).apply(any(), any(), any(), any(), any(), any());
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
        verify(facilityOwnerClaimCommandService, never()).apply(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 등록증_업로드에_실패하면_저장하지_않는다() {
        BusinessRequestDTO.ClaimRequest request = createRequest();

        givenVerified();
        when(s3ImageService.uploadDocument(any())).thenThrow(new GeneralException(ErrorStatus.IMAGE4004));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.claim(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE4004);
        verify(facilityOwnerClaimCommandService, never()).apply(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 신청_저장에_실패하면_올린_등록증을_지우고_예외를_그대로_올린다() {
        // 저장되지 않은 신청의 등록증은 아무 기록도 가리키지 않는 고아 파일이다.
        BusinessRequestDTO.ClaimRequest request = createRequest();

        givenVerified();
        when(s3ImageService.uploadDocument(any())).thenReturn(CERTIFICATE_URL);
        when(facilityOwnerClaimCommandService.apply(any(), any(), any(), any(), any(), any()))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4004));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.claim(USER_ID, FACILITY_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4004);
        verify(s3ImageService).delete(CERTIFICATE_URL);
    }

    private static final String ADDRESS = "강원 강릉시 창해로 20";
    private static final GeocodedAddress GEOCODED = new GeocodedAddress(new BigDecimal("37.8"), new BigDecimal("128.9"));

    private BusinessRequestDTO.FacilityRegisterRequest createRegisterRequest() {
        BusinessRequestDTO.FacilityRegisterRequest request = new BusinessRequestDTO.FacilityRegisterRequest();
        request.setBusinessNumber("1234567890");
        request.setRepresentativeName("홍길동");
        request.setOpeningDate("20200315");
        request.setName("새로 연 카페");
        request.setCategory(FacilityCategory.CAFE);
        request.setAddress(ADDRESS);
        request.setSidoCode("32");
        request.setSigunguCode("32210");
        request.setPetAllowed(PetAllowed.ALLOWED);
        request.setRequirements(List.of(Requirement.LEASH));
        request.setConditionRaw("리드줄 착용 시 동반 가능");
        return request;
    }

    private Region region() {
        return Region.builder().sidoCode("32").sido("강원특별자치도").sigunguCode("32210").sigungu("강릉시").build();
    }

    private void givenRegionValidAndGeocoded() {
        when(facilitySelfRegistrationCommandService.validateRegion("32", "32210")).thenReturn(region());
        when(geocodingService.geocode(ADDRESS)).thenReturn(GEOCODED);
    }

    @Test
    void registerFacility_중복_후보가_없으면_지역검증_지오코딩_국세청확인_저장_순서로_처리한다() {
        BusinessRequestDTO.FacilityRegisterRequest request = createRegisterRequest();
        givenRegionValidAndGeocoded();
        when(facilityDuplicateCandidateQueryService.findCandidates("새로 연 카페", 37.8, 128.9)).thenReturn(List.of());
        when(facilitySelfRegistrationCommandService.register(
                eq(USER_ID), eq(request), any(Region.class), eq(GEOCODED.lat()), eq(GEOCODED.lng()), any(), any()
        )).thenReturn(new BusinessResponseDTO.FacilityRegisterResult(
                20L, 30L, "새로 연 카페", FacilityCategory.CAFE, ADDRESS, ClaimStatus.APPROVED
        ));

        BusinessResponseDTO.FacilityRegisterResult result = businessCommandService.registerFacility(USER_ID, request);

        assertThat(result.facilityId()).isEqualTo(20L);
        assertThat(result.status()).isEqualTo(ClaimStatus.APPROVED);

        InOrder inOrder = inOrder(
                facilitySelfRegistrationCommandService, geocodingService,
                facilityDuplicateCandidateQueryService, businessQueryService
        );
        inOrder.verify(facilitySelfRegistrationCommandService).validateRegion("32", "32210");
        inOrder.verify(geocodingService).geocode(ADDRESS);
        inOrder.verify(facilityDuplicateCandidateQueryService).findCandidates("새로 연 카페", 37.8, 128.9);
        inOrder.verify(businessQueryService).verify("1234567890", "홍길동", "20200315");
        inOrder.verify(facilitySelfRegistrationCommandService).register(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerFacility_지역코드가_잘못되면_이후_단계를_호출하지_않는다() {
        BusinessRequestDTO.FacilityRegisterRequest request = createRegisterRequest();
        when(facilitySelfRegistrationCommandService.validateRegion("32", "32210"))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4012));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.registerFacility(USER_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4012);
        verifyNoInteractions(geocodingService, businessQueryService);
        verify(facilitySelfRegistrationCommandService, never()).register(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerFacility_지오코딩에_실패하면_이후_단계를_호출하지_않는다() {
        BusinessRequestDTO.FacilityRegisterRequest request = createRegisterRequest();
        when(facilitySelfRegistrationCommandService.validateRegion("32", "32210")).thenReturn(region());
        when(geocodingService.geocode(ADDRESS)).thenThrow(new GeneralException(ErrorStatus.BUSINESS4011));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.registerFacility(USER_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4011);
        verifyNoInteractions(businessQueryService);
        verify(facilitySelfRegistrationCommandService, never()).register(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerFacility_중복_후보가_있고_확인하지_않았으면_국세청을_부르지_않고_BUSINESS4010과_후보를_반환한다() {
        BusinessRequestDTO.FacilityRegisterRequest request = createRegisterRequest();
        // 기본값 false
        givenRegionValidAndGeocoded();
        BusinessResponseDTO.FacilityDuplicateCandidate candidate = new BusinessResponseDTO.FacilityDuplicateCandidate(
                6L, "새로 연 카페", ADDRESS, FacilityCategory.CAFE,
                com.freepets.domain.facility.entity.FacilitySource.TOUR_API, 30.0
        );
        when(facilityDuplicateCandidateQueryService.findCandidates("새로 연 카페", 37.8, 128.9))
                .thenReturn(List.of(candidate));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.registerFacility(USER_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4010);
        BusinessResponseDTO.FacilityDuplicateCandidateList payload =
                (BusinessResponseDTO.FacilityDuplicateCandidateList) exception.getResult();
        assertThat(payload.candidates()).containsExactly(candidate);
        // 국세청 쿼터를 아끼기 위해 확인되지 않은 중복 후보 단계에서 멈춘다.
        verifyNoInteractions(businessQueryService);
        verify(facilitySelfRegistrationCommandService, never()).register(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerFacility_중복_후보가_있어도_확인했으면_정상_진행한다() {
        BusinessRequestDTO.FacilityRegisterRequest request = createRegisterRequest();
        request.setDuplicateCheckAcknowledged(true);
        givenRegionValidAndGeocoded();
        BusinessResponseDTO.FacilityDuplicateCandidate candidate = new BusinessResponseDTO.FacilityDuplicateCandidate(
                6L, "새로 연 카페", ADDRESS, FacilityCategory.CAFE,
                com.freepets.domain.facility.entity.FacilitySource.TOUR_API, 30.0
        );
        when(facilityDuplicateCandidateQueryService.findCandidates("새로 연 카페", 37.8, 128.9))
                .thenReturn(List.of(candidate));
        when(facilitySelfRegistrationCommandService.register(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BusinessResponseDTO.FacilityRegisterResult(
                        20L, 30L, "새로 연 카페", FacilityCategory.CAFE, ADDRESS, ClaimStatus.APPROVED
                ));

        BusinessResponseDTO.FacilityRegisterResult result = businessCommandService.registerFacility(USER_ID, request);

        assertThat(result.facilityId()).isEqualTo(20L);
        verify(businessQueryService).verify("1234567890", "홍길동", "20200315");
    }

    @Test
    void registerFacility_국세청_확인에_실패하면_저장하지_않는다() {
        BusinessRequestDTO.FacilityRegisterRequest request = createRegisterRequest();
        givenRegionValidAndGeocoded();
        when(facilityDuplicateCandidateQueryService.findCandidates("새로 연 카페", 37.8, 128.9)).thenReturn(List.of());
        when(businessQueryService.verify("1234567890", "홍길동", "20200315"))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4001));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessCommandService.registerFacility(USER_ID, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4001);
        verify(facilitySelfRegistrationCommandService, never()).register(any(), any(), any(), any(), any(), any(), any());
    }
}
