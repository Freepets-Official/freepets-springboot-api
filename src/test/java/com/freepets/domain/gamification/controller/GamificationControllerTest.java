package com.freepets.domain.gamification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.PawAnimal;
import com.freepets.domain.gamification.entity.PawColor;
import com.freepets.domain.gamification.service.GamificationQueryService;
import com.freepets.domain.gamification.service.GamificationService;

@WebMvcTest(GamificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class GamificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GamificationQueryService gamificationQueryService;

    @MockitoBean
    private GamificationService gamificationService;

    @Test
    @DisplayName("내 게이미피케이션 상태 조회에 성공하면 200을 반환한다")
    void 내_게이미피케이션_상태_조회에_성공하면_200을_반환한다() throws Exception {
        when(gamificationQueryService.getMyStatus(isNull())).thenReturn(
                new GamificationResponseDTO.MyStatus(
                        2, 150L, 200L, PawAnimal.DOG, PawColor.RED, "개 발바닥 · 빨강", null, true,
                        List.of(new GamificationResponseDTO.BadgeSummary("FIRST_REVIEW", "첫 리뷰", "첫 리뷰를 남겼어요", null))
                )
        );

        mockMvc.perform(get("/api/v1/me/gamification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.level").value(2))
                .andExpect(jsonPath("$.result.totalXp").value(150))
                .andExpect(jsonPath("$.result.xpToNextLevel").value(200))
                .andExpect(jsonPath("$.result.tierAnimal").value("DOG"))
                .andExpect(jsonPath("$.result.tierColor").value("RED"))
                .andExpect(jsonPath("$.result.tierLabel").value("개 발바닥 · 빨강"))
                .andExpect(jsonPath("$.result.badges[0].code").value("FIRST_REVIEW"));
    }

    @Test
    @DisplayName("최대 레벨이면 xpToNextLevel 키가 응답에서 빠진다")
    void 최대_레벨이면_xpToNextLevel_키가_응답에서_빠진다() throws Exception {
        when(gamificationQueryService.getMyStatus(isNull())).thenReturn(
                new GamificationResponseDTO.MyStatus(
                        70, 999999L, null, PawAnimal.CAT, PawColor.VIOLET, "고양이 발바닥 · 보라", null, true, List.of()
                )
        );

        mockMvc.perform(get("/api/v1/me/gamification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.xpToNextLevel").doesNotExist());
    }

    @Test
    @DisplayName("레벨업 알림 토글에 성공하면 200을 반환한다")
    void 레벨업_알림_토글에_성공하면_200을_반환한다() throws Exception {
        when(gamificationService.updateLevelUpNotification(isNull(), eq(false))).thenReturn(false);

        mockMvc.perform(patch("/api/v1/me/gamification/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"levelUpNotificationEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.levelUpNotificationEnabled").value(false));
    }

    @Test
    @DisplayName("levelUpNotificationEnabled 없이 토글 요청하면 400을 반환한다")
    void levelUpNotificationEnabled_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/me/gamification/notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
