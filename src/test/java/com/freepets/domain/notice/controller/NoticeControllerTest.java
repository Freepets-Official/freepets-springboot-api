package com.freepets.domain.notice.controller;

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

import com.freepets.domain.notice.dto.NoticeResponseDTO;
import com.freepets.domain.notice.service.NoticeQueryService;

@WebMvcTest(NoticeController.class)
@AutoConfigureMockMvc(addFilters = false)
class NoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoticeQueryService noticeQueryService;

    @Test
    @DisplayName("공지 목록을 조회하면 200과 함께 목록을 반환한다")
    void 공지_목록을_조회하면_200을_반환한다() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 16, 12, 0);
        when(noticeQueryService.getNotices()).thenReturn(List.of(
                new NoticeResponseDTO.NoticeItem(1L, "점검 안내", "내용", createdAt, true)
        ));

        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].id").value(1))
                .andExpect(jsonPath("$.result[0].title").value("점검 안내"))
                .andExpect(jsonPath("$.result[0].body").value("내용"))
                .andExpect(jsonPath("$.result[0].pinned").value(true));
    }

    @Test
    @DisplayName("공지가 없으면 빈 배열을 반환한다")
    void 공지가_없으면_빈_배열을_반환한다() throws Exception {
        when(noticeQueryService.getNotices()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isArray())
                .andExpect(jsonPath("$.result").isEmpty());
    }

}
