package com.freepets.domain.business.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.service.BusinessCommandService;
import com.freepets.domain.business.service.BusinessQueryService;
import com.freepets.domain.business.service.FacilityOwnerClaimQueryService;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@WebMvcTest(BusinessController.class)
@AutoConfigureMockMvc(addFilters = false)
class BusinessControllerTest {

    private static final String VALID_BODY = """
            {"businessNumber":"1234567890","representativeName":"홍길동","openingDate":"20200315"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessQueryService businessQueryService;

    @MockitoBean
    private BusinessCommandService businessCommandService;

    @MockitoBean
    private FacilityOwnerClaimQueryService facilityOwnerClaimQueryService;

    @Test
    void verify_성공하면_200과_사업_상태를_반환한다() throws Exception {
        when(businessQueryService.verify(any()))
                .thenReturn(new BusinessResponseDTO.VerifyResult(true, "01", "계속사업자"));

        mockMvc.perform(post("/api/v1/business/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.valid").value(true))
                .andExpect(jsonPath("$.result.status").value("01"))
                .andExpect(jsonPath("$.result.statusLabel").value("계속사업자"));
    }

    @Test
    void verify_사업자등록번호_형식이_틀리면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/business/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNumber":"123-45-67890","representativeName":"홍길동","openingDate":"20200315"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.businessNumber").exists());

        // 형식은 서버에서 걸러 국세청을 부르지 않는다.
        verifyNoInteractions(businessQueryService);
    }

    @Test
    void verify_필드가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/business/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNumber":"","representativeName":"","openingDate":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.representativeName").exists())
                .andExpect(jsonPath("$.result.openingDate").exists());

        verifyNoInteractions(businessQueryService);
    }

    @Test
    void verify_휴업_폐업이면_400과_BUSINESS4002를_반환한다() throws Exception {
        when(businessQueryService.verify(any()))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4002));

        mockMvc.perform(post("/api/v1/business/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("BUSINESS4002"));
    }

    @Test
    void verify_국세청_호출에_실패하면_502를_반환한다() throws Exception {
        when(businessQueryService.verify(any()))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS5001));

        mockMvc.perform(post("/api/v1/business/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BUSINESS5001"));
    }

    private MockMultipartFile certificate() {
        return new MockMultipartFile(
                "registrationCertificate", "등록증.pdf", "application/pdf", new byte[] {1, 2, 3}
        );
    }

    /** 파일이 있어 multipart로 받는다. 목록인 requirements는 같은 이름을 반복해서 보낸다. */
    private MockMultipartHttpServletRequestBuilder claimRequest(MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/business/facilities/6/claim");
        for (MockMultipartFile file : files) {
            builder.file(file);
        }

        return builder
                .param("businessNumber", "1234567890")
                .param("representativeName", "홍길동")
                .param("openingDate", "20200315")
                .param("petAllowed", "ALLOWED")
                .param("maxWeight", "10.0")
                .param("maxWeightInclusive", "true")
                .param("requirements", "LEASH", "MUZZLE")
                .param("conditionRaw", "리드줄 착용 시 실내 동반 가능");
    }

    @Test
    void claim_성공하면_200과_대기_상태의_신청을_반환한다() throws Exception {
        when(businessCommandService.claim(any(), any(), any())).thenReturn(
                new BusinessResponseDTO.ClaimResult(11L, 6L, ClaimStatus.PENDING)
        );

        mockMvc.perform(claimRequest(certificate()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.claimId").value(11))
                .andExpect(jsonPath("$.result.facilityId").value(6))
                .andExpect(jsonPath("$.result.status").value("PENDING"));

        // 목록으로 보낸 요구조건이 그대로 바인딩되는지 — multipart로 바뀌면서 깨지기 쉬운 부분이다.
        ArgumentCaptor<BusinessRequestDTO.ClaimRequest> requestCaptor =
                ArgumentCaptor.forClass(BusinessRequestDTO.ClaimRequest.class);
        verify(businessCommandService).claim(any(), any(), requestCaptor.capture());
        BusinessRequestDTO.ClaimRequest request = requestCaptor.getValue();
        assertThat(request.getRequirements()).containsExactly(Requirement.LEASH, Requirement.MUZZLE);
        assertThat(request.getMaxWeight()).isEqualByComparingTo("10.0");
        assertThat(request.getRegistrationCertificate().getOriginalFilename()).isEqualTo("등록증.pdf");
    }

    @Test
    void claim_사업자등록증이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(claimRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.registrationCertificate").exists());

        verifyNoInteractions(businessCommandService);
    }

    @Test
    void claim_동반_여부가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(multipart("/api/v1/business/facilities/6/claim")
                        .file(certificate())
                        .param("businessNumber", "1234567890")
                        .param("representativeName", "홍길동")
                        .param("openingDate", "20200315"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.petAllowed").exists());

        verifyNoInteractions(businessCommandService);
    }

    @Test
    void claim_다른_사업자가_이미_등록한_매장이면_409를_반환한다() throws Exception {
        when(businessCommandService.claim(any(), any(), any()))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4003));

        mockMvc.perform(claimRequest(certificate()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("BUSINESS4003"));
    }

    @Test
    void claim_이미_심사_중인_신청이_있으면_409를_반환한다() throws Exception {
        when(businessCommandService.claim(any(), any(), any()))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4004));

        mockMvc.perform(claimRequest(certificate()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS4004"));
    }

    @Test
    void claim_이미_등록을_마친_내_매장이면_409를_반환한다() throws Exception {
        when(businessCommandService.claim(any(), any(), any()))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4005));

        mockMvc.perform(claimRequest(certificate()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS4005"));
    }

    @Test
    void myClaims_성공하면_200과_신청_목록을_반환한다() throws Exception {
        LocalDateTime appliedAt = LocalDateTime.of(2026, 9, 16, 10, 0);
        when(facilityOwnerClaimQueryService.getMyClaims(any())).thenReturn(
                new BusinessResponseDTO.MyClaimList(List.of(
                        new BusinessResponseDTO.MyClaim(
                                11L,
                                6L,
                                "카페 파도살롱",
                                "강원 강릉시 창해로 17",
                                ClaimStatus.PENDING,
                                appliedAt,
                                null,
                                new BigDecimal("10.00"),
                                true
                        )
                ))
        );

        mockMvc.perform(get("/api/v1/business/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.claims[0].claimId").value(11))
                .andExpect(jsonPath("$.result.claims[0].facilityId").value(6))
                .andExpect(jsonPath("$.result.claims[0].facilityName").value("카페 파도살롱"))
                .andExpect(jsonPath("$.result.claims[0].facilityAddress").value("강원 강릉시 창해로 17"))
                .andExpect(jsonPath("$.result.claims[0].status").value("PENDING"))
                .andExpect(jsonPath("$.result.claims[0].requestedMaxWeight").value(10.00))
                .andExpect(jsonPath("$.result.claims[0].requestedMaxWeightInclusive").value(true));
    }

    @Test
    void myClaims_반려된_신청은_반려_사유를_함께_반환한다() throws Exception {
        LocalDateTime appliedAt = LocalDateTime.of(2026, 9, 16, 10, 0);
        when(facilityOwnerClaimQueryService.getMyClaims(any())).thenReturn(
                new BusinessResponseDTO.MyClaimList(List.of(
                        new BusinessResponseDTO.MyClaim(
                                12L,
                                7L,
                                "카페 해변길",
                                "강원 속초시 해오름로 5",
                                ClaimStatus.REJECTED,
                                appliedAt,
                                "등록증 사업자명이 신청자와 일치하지 않습니다",
                                null,
                                null
                        )
                ))
        );

        mockMvc.perform(get("/api/v1/business/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.claims[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.result.claims[0].reviewReason").value("등록증 사업자명이 신청자와 일치하지 않습니다"));
    }

    @Test
    void myClaims_신청이_없으면_빈_목록을_반환한다() throws Exception {
        when(facilityOwnerClaimQueryService.getMyClaims(any()))
                .thenReturn(new BusinessResponseDTO.MyClaimList(List.of()));

        mockMvc.perform(get("/api/v1/business/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.claims").isEmpty());
    }

    private static final String DUPLICATE_CHECK_BODY = """
            {"name":"카페 파도살롱","address":"강원 강릉시 창해로 17"}
            """;

    @Test
    void duplicateCheck_성공하면_200과_후보_목록을_반환한다() throws Exception {
        when(businessQueryService.duplicateCheck(any())).thenReturn(
                new BusinessResponseDTO.FacilityDuplicateCandidateList(List.of(
                        new BusinessResponseDTO.FacilityDuplicateCandidate(
                                6L, "카페 파도살롱", "강원 강릉시 창해로 17", FacilityCategory.CAFE, FacilitySource.TOUR_API, 30.0
                        )
                ))
        );

        mockMvc.perform(post("/api/v1/business/facilities/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DUPLICATE_CHECK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.candidates[0].facilityId").value(6))
                .andExpect(jsonPath("$.result.candidates[0].name").value("카페 파도살롱"))
                .andExpect(jsonPath("$.result.candidates[0].source").value("TOUR_API"));
    }

    @Test
    void duplicateCheck_이름이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/business/facilities/duplicate-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","address":"강원 강릉시 창해로 17"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.name").exists());

        verifyNoInteractions(businessQueryService);
    }

    private static final String REGISTER_BODY = """
            {
              "businessNumber":"1234567890","representativeName":"홍길동","openingDate":"20200315",
              "name":"새로 연 카페","category":"CAFE","address":"강원 강릉시 창해로 20",
              "sidoCode":"32","sigunguCode":"32210",
              "petAllowed":"ALLOWED","requirements":["LEASH"],"conditionRaw":"리드줄 착용 시 동반 가능"
            }
            """;

    @Test
    void registerFacility_성공하면_201대신_200과_즉시_확정된_결과를_반환한다() throws Exception {
        when(businessCommandService.registerFacility(any(), any())).thenReturn(
                new BusinessResponseDTO.FacilityRegisterResult(
                        20L, 30L, "새로 연 카페", FacilityCategory.CAFE, "강원 강릉시 창해로 20", ClaimStatus.APPROVED
                )
        );

        mockMvc.perform(post("/api/v1/business/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.facilityId").value(20))
                .andExpect(jsonPath("$.result.claimId").value(30))
                .andExpect(jsonPath("$.result.status").value("APPROVED"));
    }

    @Test
    void registerFacility_매장명이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/business/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessNumber":"1234567890","representativeName":"홍길동","openingDate":"20200315",
                                  "name":"","category":"CAFE","address":"강원 강릉시 창해로 20",
                                  "sidoCode":"32","petAllowed":"ALLOWED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.name").exists());

        verifyNoInteractions(businessCommandService);
    }

    @Test
    void registerFacility_중복_후보가_있으면_409와_후보_목록을_반환한다() throws Exception {
        when(businessCommandService.registerFacility(any(), any())).thenThrow(new GeneralException(
                ErrorStatus.BUSINESS4010,
                new BusinessResponseDTO.FacilityDuplicateCandidateList(List.of(
                        new BusinessResponseDTO.FacilityDuplicateCandidate(
                                6L, "새로 연 카페", "강원 강릉시 창해로 20", FacilityCategory.CAFE, FacilitySource.TOUR_API, 30.0
                        )
                ))
        ));

        mockMvc.perform(post("/api/v1/business/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS4010"))
                .andExpect(jsonPath("$.result.candidates[0].name").value("새로 연 카페"));
    }

    @Test
    void registerFacility_지역코드가_잘못되면_400을_반환한다() throws Exception {
        when(businessCommandService.registerFacility(any(), any()))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4012));

        mockMvc.perform(post("/api/v1/business/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS4012"));
    }
}
