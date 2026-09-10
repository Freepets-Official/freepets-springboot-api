package com.freepets.domain.calendar.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

import com.freepets.domain.calendar.dto.CalendarEventResponseDTO;
import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.calendar.entity.RepeatType;
import com.freepets.domain.calendar.service.CalendarEventCommandService;
import com.freepets.domain.calendar.service.CalendarEventQueryService;
import com.freepets.domain.calendar.service.CalendarMedLogCommandService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@WebMvcTest(CalendarEventController.class)
@AutoConfigureMockMvc(addFilters = false)
class CalendarEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarEventQueryService calendarEventQueryService;

    @MockitoBean
    private CalendarEventCommandService calendarEventCommandService;

    @MockitoBean
    private CalendarMedLogCommandService calendarMedLogCommandService;

    private CalendarEventResponseDTO.EventOccurrence medOccurrence(boolean taken) {
        return new CalendarEventResponseDTO.EventOccurrence(
                1L, 2L, "댕댕이", CalendarEventType.MED, "관절 영양제",
                LocalDate.of(2026, 9, 10), null, RepeatType.DAILY, true, null, taken
        );
    }

    private CalendarEventResponseDTO.EventOccurrence vaccineOccurrence() {
        return new CalendarEventResponseDTO.EventOccurrence(
                3L, null, null, CalendarEventType.VACCINE, "종합백신 2차",
                LocalDate.of(2026, 9, 10), null, RepeatType.NONE, true, null, null
        );
    }

    @Test
    @DisplayName("date로 조회하면 200")
    void date로_조회하면_200() throws Exception {
        when(calendarEventQueryService.getEvents(any(), eq(LocalDate.of(2026, 9, 10)), isNull()))
                .thenReturn(new CalendarEventResponseDTO.EventList(List.of(medOccurrence(true))));

        mockMvc.perform(get("/api/v1/calendar-events").param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.events[0].taken").value(true));
    }

    @Test
    @DisplayName("month로 조회하면 200")
    void month로_조회하면_200() throws Exception {
        when(calendarEventQueryService.getEvents(any(), isNull(), any()))
                .thenReturn(new CalendarEventResponseDTO.EventList(List.of(vaccineOccurrence())));

        mockMvc.perform(get("/api/v1/calendar-events").param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.events[0].eventType").value("VACCINE"));
    }

    @Test
    @DisplayName("date와 month 둘 다 없으면 400")
    void date_month_둘다_없으면_400() throws Exception {
        when(calendarEventQueryService.getEvents(any(), isNull(), isNull()))
                .thenThrow(new GeneralException(ErrorStatus.CALENDAR4003));

        mockMvc.perform(get("/api/v1/calendar-events"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALENDAR4003"));
    }

    @Test
    @DisplayName("MED 응답엔 taken 키가 있고, 비-MED 응답엔 없다")
    void MED_응답엔_taken_있고_비MED엔_없다() throws Exception {
        when(calendarEventQueryService.getEvents(any(), eq(LocalDate.of(2026, 9, 10)), isNull()))
                .thenReturn(new CalendarEventResponseDTO.EventList(List.of(medOccurrence(false), vaccineOccurrence())));

        mockMvc.perform(get("/api/v1/calendar-events").param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.events[0].taken").value(false))
                .andExpect(jsonPath("$.result.events[1].taken").doesNotExist());
    }

    @Test
    @DisplayName("petId 없는 occurrence는 petId/petName 키가 응답에서 빠진다")
    void petId_없으면_키가_빠진다() throws Exception {
        when(calendarEventQueryService.getEvents(any(), eq(LocalDate.of(2026, 9, 10)), isNull()))
                .thenReturn(new CalendarEventResponseDTO.EventList(List.of(vaccineOccurrence())));

        mockMvc.perform(get("/api/v1/calendar-events").param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.events[0].petId").doesNotExist())
                .andExpect(jsonPath("$.result.events[0].petName").doesNotExist());
    }

    @Test
    @DisplayName("일정 등록 성공")
    void 일정_등록_성공() throws Exception {
        when(calendarEventCommandService.createEvent(any(), any()))
                .thenReturn(new CalendarEventResponseDTO.CreateResult(1L));

        mockMvc.perform(post("/api/v1/calendar-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"VACCINE","title":"종합백신 2차","date":"2026-09-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.eventId").value(1));
    }

    @Test
    @DisplayName("제목 없이 등록하면 400")
    void 제목_없이_등록하면_400() throws Exception {
        mockMvc.perform(post("/api/v1/calendar-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"VACCINE","date":"2026-09-10"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("일정 전체 수정 성공")
    void 일정_전체_수정_성공() throws Exception {
        when(calendarEventCommandService.updateEvent(any(), eq(1L), any()))
                .thenReturn(new CalendarEventResponseDTO.EventDetail(
                        1L, null, null, CalendarEventType.VACCINE, "수정된 제목",
                        LocalDate.of(2026, 9, 11), null, RepeatType.NONE, true, null,
                        LocalDateTime.now(), LocalDateTime.now()
                ));

        mockMvc.perform(patch("/api/v1/calendar-events/{eventId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"VACCINE","title":"수정된 제목","date":"2026-09-11"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("수정된 제목"));
    }

    @Test
    @DisplayName("타인 일정 수정은 403")
    void 타인_일정_수정은_403() throws Exception {
        when(calendarEventCommandService.updateEvent(any(), eq(1L), any()))
                .thenThrow(new GeneralException(ErrorStatus.CALENDAR4002));

        mockMvc.perform(patch("/api/v1/calendar-events/{eventId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"VACCINE","title":"제목","date":"2026-09-11"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CALENDAR4002"));
    }

    @Test
    @DisplayName("일정 삭제 성공")
    void 일정_삭제_성공() throws Exception {
        when(calendarEventCommandService.deleteEvent(any(), eq(1L)))
                .thenReturn(new CalendarEventResponseDTO.DeleteResult(1L));

        mockMvc.perform(delete("/api/v1/calendar-events/{eventId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.eventId").value(1));
    }

    @Test
    @DisplayName("알림 토글 성공")
    void 알림_토글_성공() throws Exception {
        when(calendarEventCommandService.updateReminder(any(), eq(1L), any()))
                .thenReturn(new CalendarEventResponseDTO.ReminderResult(1L, true));

        mockMvc.perform(patch("/api/v1/calendar-events/{eventId}/reminder", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reminderEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reminderEnabled").value(true));
    }

    @Test
    @DisplayName("reminderEnabled 없이 토글 요청하면 400")
    void reminderEnabled_없으면_400() throws Exception {
        mockMvc.perform(patch("/api/v1/calendar-events/{eventId}/reminder", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("복용 체크 성공")
    void 복용_체크_성공() throws Exception {
        when(calendarMedLogCommandService.markTaken(any(), eq(1L), eq(LocalDate.of(2026, 9, 10))))
                .thenReturn(new CalendarEventResponseDTO.MedLogResult(1L, LocalDate.of(2026, 9, 10), true));

        mockMvc.perform(put("/api/v1/calendar-events/{eventId}/med-log/{date}", 1L, "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.taken").value(true));
    }

    @Test
    @DisplayName("복용 체크 해제 성공")
    void 복용_체크_해제_성공() throws Exception {
        when(calendarMedLogCommandService.unmarkTaken(any(), eq(1L), eq(LocalDate.of(2026, 9, 10))))
                .thenReturn(new CalendarEventResponseDTO.MedLogResult(1L, LocalDate.of(2026, 9, 10), false));

        mockMvc.perform(delete("/api/v1/calendar-events/{eventId}/med-log/{date}", 1L, "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.taken").value(false));
    }
}
