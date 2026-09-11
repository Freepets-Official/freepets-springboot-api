package com.freepets.domain.report.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.report.dto.FacilityConditionInquiryResponseDTO;
import com.freepets.domain.report.service.FacilityConditionInquiryCommandService;
import com.freepets.domain.report.service.FacilityConditionInquiryQueryService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@WebMvcTest(FacilityConditionInquiryController.class)
@AutoConfigureMockMvc(addFilters = false)
class FacilityConditionInquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityConditionInquiryCommandService facilityConditionInquiryCommandService;

    @MockitoBean
    private FacilityConditionInquiryQueryService facilityConditionInquiryQueryService;

    @Test
    @DisplayName("본문 없이 요청하면 200을 반환한다")
    void 본문_없이_요청하면_200을_반환한다() throws Exception {
        when(facilityConditionInquiryCommandService.inquire(isNull(), eq(7L), isNull()))
                .thenReturn(new FacilityConditionInquiryResponseDTO.InquireResult(100L, 7L));

        mockMvc.perform(post("/api/v1/facilities/{facilityId}/condition-inquiries", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inquiryId").value(100))
                .andExpect(jsonPath("$.result.facilityId").value(7));
    }

    @Test
    @DisplayName("메모를 담아 요청하면 그대로 전달된다")
    void 메모를_담아_요청하면_그대로_전달된다() throws Exception {
        when(facilityConditionInquiryCommandService.inquire(isNull(), eq(7L), eq("실내 동반 범위가 궁금해요")))
                .thenReturn(new FacilityConditionInquiryResponseDTO.InquireResult(100L, 7L));

        mockMvc.perform(post("/api/v1/facilities/{facilityId}/condition-inquiries", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"실내 동반 범위가 궁금해요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inquiryId").value(100));
    }

    @Test
    @DisplayName("24시간 내 중복 요청이면 409를 반환한다")
    void 이십사시간_내_중복_요청이면_409를_반환한다() throws Exception {
        when(facilityConditionInquiryCommandService.inquire(isNull(), eq(7L), isNull()))
                .thenThrow(new GeneralException(ErrorStatus.REPORT4002));

        mockMvc.perform(post("/api/v1/facilities/{facilityId}/condition-inquiries", 7L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT4002"));
    }

    @Test
    @DisplayName("요청 수 조회에 성공하면 200을 반환한다")
    void 요청_수_조회에_성공하면_200을_반환한다() throws Exception {
        when(facilityConditionInquiryQueryService.getCount(7L))
                .thenReturn(new FacilityConditionInquiryResponseDTO.CountResult(7L, 12L));

        mockMvc.perform(get("/api/v1/facilities/{facilityId}/condition-inquiries/count", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.count").value(12));
    }
}
