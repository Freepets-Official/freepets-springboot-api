package com.freepets.domain.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.report.dto.DenialReportResponseDTO;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.report.entity.ReportStatus;
import com.freepets.domain.report.entity.ReportType;
import com.freepets.domain.report.service.DenialReportCommandService;
import com.freepets.domain.report.service.DenialReportQueryService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@WebMvcTest(DenialReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class DenialReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DenialReportCommandService denialReportCommandService;

    @MockitoBean
    private DenialReportQueryService denialReportQueryService;

    private DenialReportResponseDTO.Report report(boolean mine) {
        return new DenialReportResponseDTO.Report(
                1L, 7L, ReportType.DENIED, "현장 거부 · 체중 초과", DenialReason.WEIGHT,
                2, false, mine, true, ReportStatus.APPLIED, LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("원터치 제보 정상 접수")
    void 원터치_제보_정상_접수() throws Exception {
        when(denialReportCommandService.report(any(), eq(7L), eq(DenialReason.WEIGHT))).thenReturn(report(true));

        mockMvc.perform(post("/api/v1/facilities/{facilityId}/denial-reports", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"WEIGHT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reason").value("WEIGHT"))
                .andExpect(jsonPath("$.result.mine").value(true));
    }

    @Test
    @DisplayName("reason 없이 보내면 400")
    void reason_없으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/{facilityId}/denial-reports", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("24시간 내 재제보는 409")
    void 재제보는_409() throws Exception {
        when(denialReportCommandService.report(any(), eq(7L), eq(DenialReason.WEIGHT)))
                .thenThrow(new GeneralException(ErrorStatus.REPORT4001));

        mockMvc.perform(post("/api/v1/facilities/{facilityId}/denial-reports", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"WEIGHT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT4001"));
    }

    @Test
    @DisplayName("최근 제보 목록 조회")
    void 최근_제보_목록_조회() throws Exception {
        when(denialReportQueryService.getRecent(eq(7L), any())).thenReturn(List.of(report(false)));

        mockMvc.perform(get("/api/v1/facilities/{facilityId}/denial-reports/recent", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].mine").value(false));
    }

    @Test
    @DisplayName("내 제보가 없으면 result가 null")
    void 내_제보_없으면_null() throws Exception {
        when(denialReportQueryService.getMine(eq(7L), any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/facilities/{facilityId}/denial-reports/mine", 7L))
                .andExpect(status().isOk())
                // ApiResponse가 @JsonInclude(NON_NULL)이라 result가 null이면 키 자체가 빠진다.
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("내가 판별받은 시설의 거부 알림 조회")
    void 거부_알림_조회() throws Exception {
        DenialReportResponseDTO.DenialAlert alert = new DenialReportResponseDTO.DenialAlert(
                new DenialReportResponseDTO.FacilityRef(10L, "테라로사 커피공장"),
                new DenialReportResponseDTO.AlertReport(1L, DenialReason.INDOOR, LocalDateTime.now())
        );
        when(denialReportQueryService.getMyDenialAlerts(any())).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/me/denial-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].facility.name").value("테라로사 커피공장"))
                .andExpect(jsonPath("$.result[0].report.reason").value("INDOOR"));
    }
}
