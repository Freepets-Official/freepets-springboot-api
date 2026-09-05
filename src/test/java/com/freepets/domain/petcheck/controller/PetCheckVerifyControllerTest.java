package com.freepets.domain.petcheck.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.service.PetCheckQueryService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@WebMvcTest(PetCheckVerifyController.class)
@AutoConfigureMockMvc(addFilters = false)
class PetCheckVerifyControllerTest {

    private static final String VERIFY_PATH = "/verify/FP-ABC1234567";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PetCheckQueryService petCheckQueryService;

    @Test
    @DisplayName("정상 코드면 200과 함께 시설명·반려동물 정보가 담긴 HTML을 내려준다")
    void 정상_코드면_200과_HTML을_내려준다() throws Exception {
        PetCheckResponseDTO.VerifyPetInfo pet = new PetCheckResponseDTO.VerifyPetInfo(
                "몽이",
                "말티즈",
                new BigDecimal("3.20"),
                "소형견",
                true,
                null
        );
        PetCheckResponseDTO.VerifyPage page = new PetCheckResponseDTO.VerifyPage(
                "FP-ABC1234567",
                PetCheckResult.CONDITIONAL,
                "테라로자 커피공장",
                pet,
                List.of("리드줄 필수 착용"),
                "몽이는 리드줄만 착용하면 이용 가능합니다",
                "야외 좌석에 한해 반려동물 동반이 가능합니다.",
                LocalDateTime.now().minusDays(60),
                LocalDateTime.now()
        );
        when(petCheckQueryService.getVerifyPage("FP-ABC1234567")).thenReturn(page);

        mockMvc.perform(get(VERIFY_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("테라로자 커피공장")))
                .andExpect(content().string(containsString("몽이")))
                .andExpect(content().string(containsString("리드줄 필수 착용")));
    }

    @Test
    @DisplayName("존재하지 않는 코드면 JSON이 아니라 404 HTML 페이지를 내려준다")
    void 존재하지_않는_코드면_404_HTML을_내려준다() throws Exception {
        when(petCheckQueryService.getVerifyPage("FP-NOPE")).thenThrow(new GeneralException(ErrorStatus.PETCHECK4001));

        mockMvc.perform(get("/verify/FP-NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("해당 판별 기록이 존재하지 않습니다")));
    }

    @Test
    @DisplayName("반려동물이 삭제된 판별 결과는 아이 정보 없이도 200을 내려준다")
    void 반려동물이_삭제됐어도_200을_내려준다() throws Exception {
        PetCheckResponseDTO.VerifyPage page = new PetCheckResponseDTO.VerifyPage(
                "FP-ABC1234567",
                PetCheckResult.ALLOWED,
                "테라로자 커피공장",
                null,
                List.of(),
                "모든 조건을 충족해 출입 가능합니다",
                null,
                null,
                LocalDateTime.now()
        );
        when(petCheckQueryService.getVerifyPage("FP-ABC1234567")).thenReturn(page);

        mockMvc.perform(get(VERIFY_PATH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("판별 당시 정보가 더 이상 없습니다")));
    }

    @Test
    @DisplayName("예상하지 못한 예외가 나도 JSON이 아니라 500 HTML 페이지를 내려준다")
    void 예상하지_못한_예외도_500_HTML을_내려준다() throws Exception {
        // GeneralException이 아닌 임의의 런타임 예외 — GlobalExceptionHandler로 새면 JSON이 나간다.
        when(petCheckQueryService.getVerifyPage("FP-ABC1234567")).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get(VERIFY_PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(ErrorStatus.COMMON500.getMessage())));
    }
}
