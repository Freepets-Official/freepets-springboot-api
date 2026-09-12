package com.freepets.domain.business.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.service.BusinessQueryService;
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
}
