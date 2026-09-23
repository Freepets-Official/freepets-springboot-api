package com.freepets.domain.gamification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.PawColor;
import com.freepets.domain.gamification.entity.XpSourceType;
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
                        2, 150L, 200L, PawColor.RED, 60, "빨강 60% 발바닥", null, true,
                        List.of(new GamificationResponseDTO.BadgeSummary("REVIEW_BRONZE", "리뷰 동", "리뷰를 1개 작성했어요", null)),
                        List.of(new GamificationResponseDTO.BadgeProgress(
                                "REVIEW", "리뷰", 12L,
                                List.of(new GamificationResponseDTO.TierProgress("BRONZE", 1, null))
                        ))
                )
        );

        mockMvc.perform(get("/api/v1/me/gamification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.level").value(2))
                .andExpect(jsonPath("$.result.totalXp").value(150))
                .andExpect(jsonPath("$.result.xpToNextLevel").value(200))
                .andExpect(jsonPath("$.result.tierColor").value("RED"))
                .andExpect(jsonPath("$.result.tierOpacityPercent").value(60))
                .andExpect(jsonPath("$.result.tierLabel").value("빨강 60% 발바닥"))
                .andExpect(jsonPath("$.result.badges[0].code").value("REVIEW_BRONZE"))
                .andExpect(jsonPath("$.result.progress[0].family").value("REVIEW"))
                .andExpect(jsonPath("$.result.progress[0].count").value(12))
                .andExpect(jsonPath("$.result.progress[0].tiers[0].tier").value("BRONZE"));
    }

    @Test
    @DisplayName("최대 레벨이면 xpToNextLevel 키가 응답에서 빠진다")
    void 최대_레벨이면_xpToNextLevel_키가_응답에서_빠진다() throws Exception {
        when(gamificationQueryService.getMyStatus(isNull())).thenReturn(
                new GamificationResponseDTO.MyStatus(
                        40, 78000L, null, PawColor.RAINBOW, 0,
                        "무지개 0% 발바닥", null, true, List.of(), List.of()
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

    @Test
    @DisplayName("오늘의 퀘스트 조회에 성공하면 200을 반환한다")
    void 오늘의_퀘스트_조회에_성공하면_200을_반환한다() throws Exception {
        when(gamificationQueryService.getTodayQuests(isNull())).thenReturn(
                new GamificationResponseDTO.QuestList(
                        LocalDateTime.of(2026, 9, 18, 15, 0, 0),
                        List.of(
                                new GamificationResponseDTO.Quest(XpSourceType.PETCHECK, "판별하기", 3L, 10, 15L),
                                new GamificationResponseDTO.Quest(XpSourceType.REVIEW, "리뷰 남기기", 0L, 5, 0L)
                        )
                )
        );

        // resetsAt의 "Z" 접미사는 JacksonConfig가 붙이는데, 그 빈은 @WebMvcTest 슬라이스에
        // 로드되지 않아 여기서는 접미사 없는 순수 LocalDateTime.toString()으로 나간다(다른
        // 컨트롤러 테스트들도 LocalDateTime 필드의 "Z" 접미사는 검증하지 않는 이유와 같다).
        // 실제 운영 환경(전체 컨텍스트)에서는 JacksonConfig가 로드돼 "Z"가 붙는다.
        mockMvc.perform(get("/api/v1/me/gamification/quests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resetsAt").value("2026-09-18T15:00:00"))
                .andExpect(jsonPath("$.result.quests[0].sourceType").value("PETCHECK"))
                .andExpect(jsonPath("$.result.quests[0].label").value("판별하기"))
                .andExpect(jsonPath("$.result.quests[0].completed").value(3))
                .andExpect(jsonPath("$.result.quests[0].target").value(10))
                .andExpect(jsonPath("$.result.quests[0].earnedXpToday").value(15))
                .andExpect(jsonPath("$.result.quests[1].sourceType").value("REVIEW"))
                .andExpect(jsonPath("$.result.quests[1].completed").value(0));
    }
}
