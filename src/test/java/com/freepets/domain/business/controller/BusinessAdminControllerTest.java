package com.freepets.domain.business.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.service.FacilityOwnerClaimAdminCommandService;
import com.freepets.domain.business.service.FacilityOwnerClaimAdminQueryService;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

/**
 * 권한 검사(관리자만 접근 가능)는 여기서 다시 확인하지 않는다 — {@code addFilters = false}라 필터가
 * 아예 안 돌고, 그 검사는 {@code SecurityFilterChainTest}(1단계)가 이미 맡고 있다.
 */
@WebMvcTest(BusinessAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class BusinessAdminControllerTest {

    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 9, 16, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityOwnerClaimAdminQueryService facilityOwnerClaimAdminQueryService;

    @MockitoBean
    private FacilityOwnerClaimAdminCommandService facilityOwnerClaimAdminCommandService;

    private BusinessResponseDTO.AdminClaim adminClaim() {
        return new BusinessResponseDTO.AdminClaim(
                11L, 6L, "카페 파도살롱", "강원 강릉시 창해로 17",
                1L, "사장님", "123-45-*****", APPLIED_AT,
                PetAllowed.ALLOWED, new BigDecimal("10.00"), true, List.of(Requirement.LEASH),
                "리드줄 착용 시 실내 동반 가능",
                "https://bucket.s3.ap-northeast-2.amazonaws.com/certificate.pdf",
                ClaimStatus.PENDING, APPLIED_AT, null, null, false
        );
    }

    @Test
    void getClaims_성공하면_200과_목록을_반환한다() throws Exception {
        when(facilityOwnerClaimAdminQueryService.getClaims(eq(ClaimStatus.PENDING), anyInt(), anyInt()))
                .thenReturn(new BusinessResponseDTO.AdminClaimList(
                        List.of(adminClaim()),
                        new BusinessResponseDTO.PageInfo(0, 20, 1, false)
                ));

        mockMvc.perform(get("/api/v1/admin/business/claims").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.claims[0].claimId").value(11))
                .andExpect(jsonPath("$.result.claims[0].applicantNickname").value("사장님"))
                .andExpect(jsonPath("$.result.claims[0].hasApprovedOwner").value(false))
                .andExpect(jsonPath("$.result.pageInfo.totalElements").value(1));
    }

    @Test
    void getClaims_상태_기본값은_PENDING이다() throws Exception {
        when(facilityOwnerClaimAdminQueryService.getClaims(eq(ClaimStatus.PENDING), anyInt(), anyInt()))
                .thenReturn(new BusinessResponseDTO.AdminClaimList(List.of(), new BusinessResponseDTO.PageInfo(0, 20, 0, false)));

        mockMvc.perform(get("/api/v1/admin/business/claims"))
                .andExpect(status().isOk());
    }

    @Test
    void getClaims_잘못된_상태_값이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/business/claims").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(facilityOwnerClaimAdminQueryService);
    }

    @Test
    void approve_성공하면_200과_승인_결과를_반환한다() throws Exception {
        when(facilityOwnerClaimAdminCommandService.approve(any(), eq(11L)))
                .thenReturn(new BusinessResponseDTO.AdminClaimActionResult(11L, 6L, ClaimStatus.APPROVED));

        mockMvc.perform(post("/api/v1/admin/business/claims/11/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("APPROVED"));
    }

    @Test
    void approve_존재하지_않는_신청이면_404를_반환한다() throws Exception {
        when(facilityOwnerClaimAdminCommandService.approve(any(), eq(11L)))
                .thenThrow(new GeneralException(ErrorStatus.BUSINESS4006));

        mockMvc.perform(post("/api/v1/admin/business/claims/11/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUSINESS4006"));
    }

    @Test
    void reject_성공하면_200과_반려_결과를_반환한다() throws Exception {
        when(facilityOwnerClaimAdminCommandService.reject(any(), eq(11L), eq("등록증 상호 불일치")))
                .thenReturn(new BusinessResponseDTO.AdminClaimActionResult(11L, 6L, ClaimStatus.REJECTED));

        mockMvc.perform(post("/api/v1/admin/business/claims/11/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"등록증 상호 불일치"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"));
    }

    @Test
    void reject_사유가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/business/claims/11/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(facilityOwnerClaimAdminCommandService);
    }

    @Test
    void revoke_성공하면_200과_해제_결과를_반환한다() throws Exception {
        when(facilityOwnerClaimAdminCommandService.revoke(any(), eq(11L), eq("이의 제기로 소유권 회수")))
                .thenReturn(new BusinessResponseDTO.AdminClaimActionResult(11L, 6L, ClaimStatus.REVOKED));

        mockMvc.perform(post("/api/v1/admin/business/claims/11/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"이의 제기로 소유권 회수"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REVOKED"));
    }

    @Test
    void revoke_사유가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/business/claims/11/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(facilityOwnerClaimAdminCommandService);
    }
}
