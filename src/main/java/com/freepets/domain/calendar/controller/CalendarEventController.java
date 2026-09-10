package com.freepets.domain.calendar.controller;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.calendar.dto.CalendarEventRequestDTO;
import com.freepets.domain.calendar.dto.CalendarEventResponseDTO;
import com.freepets.domain.calendar.service.CalendarEventCommandService;
import com.freepets.domain.calendar.service.CalendarEventQueryService;
import com.freepets.domain.calendar.service.CalendarMedLogCommandService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/calendar-events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventQueryService calendarEventQueryService;
    private final CalendarEventCommandService calendarEventCommandService;
    private final CalendarMedLogCommandService calendarMedLogCommandService;

    // date와 month 중 정확히 하나만 지정해야 한다 — 둘 다/둘 다 아니면 서비스가 CALENDAR4003으로
    // 막는다(둘 다 필수가 아니라 선택이라 컨트롤러 어노테이션만으론 이 상호배타 조건을 표현할 수
    // 없어 서비스 계층에서 검증한다).
    @GetMapping
    public ApiResponse<CalendarEventResponseDTO.EventList> getEvents(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) {
        return ApiResponse.onSuccess(
                calendarEventQueryService.getEvents(userId, date, month)
        );
    }

    // 여행 사진(선택) 첨부를 받으려고 JSON 대신 multipart/form-data로 받는다(Pet.profile과
    // 같은 방식) — 그 외 필드(eventType, date 등)는 그대로 폼 필드로 바뀔 뿐 검증/바인딩은
    // JSON일 때와 동일하게 동작한다.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CalendarEventResponseDTO.CreateResult> createEvent(
            @AuthenticationPrincipal Long userId,
            @Valid @ModelAttribute CalendarEventRequestDTO.CreateRequest request
    ) {
        return ApiResponse.onSuccess(
                calendarEventCommandService.createEvent(userId, request)
        );
    }

    @PatchMapping(value = "/{eventId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CalendarEventResponseDTO.EventDetail> updateEvent(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId,
            @Valid @ModelAttribute CalendarEventRequestDTO.UpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                calendarEventCommandService.updateEvent(userId, eventId, request)
        );
    }

    @DeleteMapping("/{eventId}")
    public ApiResponse<CalendarEventResponseDTO.DeleteResult> deleteEvent(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId
    ) {
        return ApiResponse.onSuccess(
                calendarEventCommandService.deleteEvent(userId, eventId)
        );
    }

    @PatchMapping("/{eventId}/reminder")
    public ApiResponse<CalendarEventResponseDTO.ReminderResult> updateReminder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody CalendarEventRequestDTO.ReminderRequest request
    ) {
        return ApiResponse.onSuccess(
                calendarEventCommandService.updateReminder(userId, eventId, request)
        );
    }

    @PutMapping("/{eventId}/med-log/{date}")
    public ApiResponse<CalendarEventResponseDTO.MedLogResult> markMedTaken(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.onSuccess(
                calendarMedLogCommandService.markTaken(userId, eventId, date)
        );
    }

    @DeleteMapping("/{eventId}/med-log/{date}")
    public ApiResponse<CalendarEventResponseDTO.MedLogResult> unmarkMedTaken(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.onSuccess(
                calendarMedLogCommandService.unmarkTaken(userId, eventId, date)
        );
    }
}
