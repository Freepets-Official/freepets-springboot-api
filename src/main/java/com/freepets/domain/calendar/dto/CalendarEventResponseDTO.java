package com.freepets.domain.calendar.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.calendar.entity.RepeatType;

public class CalendarEventResponseDTO {

    private CalendarEventResponseDTO() {}

    // petId/petName/time/notes는 없을 수 있고(전체 적용, 시간 미지정, 메모 없음), taken은
    // MED가 아닌 일정엔 아예 해당하지 않는 개념이라 — null인 필드는 키 자체를 응답에서 뺀다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventOccurrence(
            Long eventId,
            Long petId,
            String petName,
            CalendarEventType eventType,
            String title,
            LocalDate date, // 발생일(occurrence date) — startDate가 아니라 이 조회에서 실제로 해당하는 날짜
            LocalTime time,
            RepeatType repeatType,
            boolean reminderEnabled,
            String notes,
            Boolean taken, // MED가 아니면 null(키 생략). MED면 true/false가 항상 채워진다.
            String photoUrl
    ) {}

    public record EventList(
            List<EventOccurrence> events
    ) {}

    public record CreateResult(
            Long eventId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventDetail(
            Long eventId,
            Long petId,
            String petName,
            CalendarEventType eventType,
            String title,
            LocalDate date,
            LocalTime time,
            RepeatType repeatType,
            boolean reminderEnabled,
            String notes,
            String photoUrl,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record DeleteResult(
            Long eventId
    ) {}

    public record ReminderResult(
            Long eventId,
            boolean reminderEnabled
    ) {}

    public record MedLogResult(
            Long eventId,
            LocalDate date,
            boolean taken
    ) {}
}
