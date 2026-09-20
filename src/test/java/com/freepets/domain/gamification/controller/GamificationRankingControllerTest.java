package com.freepets.domain.gamification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.PawColor;
import com.freepets.domain.gamification.service.RankingQueryService;

@WebMvcTest(GamificationRankingController.class)
@AutoConfigureMockMvc(addFilters = false)
class GamificationRankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankingQueryService rankingQueryService;

    @Test
    @DisplayName("전국 랭킹 조회에 성공하면 200을 반환한다")
    void 전국_랭킹_조회에_성공하면_200을_반환한다() throws Exception {
        GamificationResponseDTO.MyRanking me = new GamificationResponseDTO.MyRanking(
                12L, 340L, 1240L, 6, PawColor.GREEN, 40, true
        );
        GamificationResponseDTO.RankingItem item = new GamificationResponseDTO.RankingItem(
                1L, 8L, "강릉댕댕", 9800L, 15, PawColor.BLUE, 0, "보리", "https://example.com/pet.jpg", false
        );
        when(rankingQueryService.getNationalRanking(isNull(), eq(0), eq(20))).thenReturn(
                new GamificationResponseDTO.RankingResult(
                        "NATION", me, List.of(item), 340L, LocalDateTime.of(2026, 9, 20, 3, 0)
                )
        );

        mockMvc.perform(get("/api/v1/gamification/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.scope").value("NATION"))
                .andExpect(jsonPath("$.result.me.rank").value(12))
                .andExpect(jsonPath("$.result.me.participantCount").value(340))
                .andExpect(jsonPath("$.result.items[0].nickname").value("강릉댕댕"))
                .andExpect(jsonPath("$.result.items[0].petName").value("보리"))
                .andExpect(jsonPath("$.result.items[0].petPhotoUrl").value("https://example.com/pet.jpg"))
                .andExpect(jsonPath("$.result.items[0].isMe").value(false))
                .andExpect(jsonPath("$.result.total").value(340));
    }

    @Test
    @DisplayName("토큰 없이 호출해도(게스트) 200을 반환하고 me가 응답에서 빠진다")
    void 게스트로_호출해도_200을_반환하고_me가_빠진다() throws Exception {
        when(rankingQueryService.getNationalRanking(isNull(), eq(0), eq(20))).thenReturn(
                new GamificationResponseDTO.RankingResult(
                        "NATION", null, List.of(), 0L, LocalDateTime.of(2026, 9, 20, 3, 0)
                )
        );

        mockMvc.perform(get("/api/v1/gamification/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.me").doesNotExist());
    }

    @Test
    @DisplayName("page·size 쿼리 파라미터를 그대로 서비스에 전달한다")
    void page_size_쿼리_파라미터를_그대로_전달한다() throws Exception {
        when(rankingQueryService.getNationalRanking(isNull(), eq(2), eq(10))).thenReturn(
                new GamificationResponseDTO.RankingResult(
                        "NATION", null, List.of(), 0L, LocalDateTime.of(2026, 9, 20, 3, 0)
                )
        );

        mockMvc.perform(get("/api/v1/gamification/ranking").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
    }
}
