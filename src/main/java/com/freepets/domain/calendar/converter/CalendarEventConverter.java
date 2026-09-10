package com.freepets.domain.calendar.converter;

import java.time.LocalDate;
import java.util.List;

import com.freepets.domain.calendar.dto.CalendarEventRequestDTO;
import com.freepets.domain.calendar.dto.CalendarEventResponseDTO;
import com.freepets.domain.calendar.entity.CalendarEvent;
import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.User;

public class CalendarEventConverter {

    private CalendarEventConverter() {}

    public static CalendarEvent toEvent(
            CalendarEventRequestDTO.CreateRequest request,
            User user,
            Pet pet,
            String photoUrl
    ) {
        return CalendarEvent.builder()
                .user(user)
                .pet(pet)
                .eventType(request.getEventType())
                .title(request.getTitle())
                .startDate(request.getDate())
                .eventTime(request.getTime())
                .repeatType(request.getRepeatType())
                .reminderEnabled(request.isReminderEnabled())
                .notes(request.getNotes())
                .photoUrl(photoUrl)
                .build();
    }

    // taken은 이 occurrence가 MED 타입일 때만 채운다(그 외엔 null → 응답에서 키 자체가 빠짐).
    public static CalendarEventResponseDTO.EventOccurrence toEventOccurrence(
            CalendarEvent event,
            LocalDate occurrenceDate,
            Boolean taken
    ) {
        Pet pet = event.getPet();

        return new CalendarEventResponseDTO.EventOccurrence(
                event.getEventId(),
                pet == null ? null : pet.getPetId(),
                pet == null ? null : pet.getName(),
                event.getEventType(),
                event.getTitle(),
                occurrenceDate,
                event.getEventTime(),
                event.getRepeatType(),
                event.isReminderEnabled(),
                event.getNotes(),
                event.getEventType() == CalendarEventType.MED ? taken : null,
                event.getPhotoUrl()
        );
    }

    public static CalendarEventResponseDTO.EventList toEventList(
            List<CalendarEventResponseDTO.EventOccurrence> occurrences
    ) {
        return new CalendarEventResponseDTO.EventList(occurrences);
    }

    public static CalendarEventResponseDTO.EventDetail toEventDetail(CalendarEvent event) {
        Pet pet = event.getPet();

        return new CalendarEventResponseDTO.EventDetail(
                event.getEventId(),
                pet == null ? null : pet.getPetId(),
                pet == null ? null : pet.getName(),
                event.getEventType(),
                event.getTitle(),
                event.getStartDate(),
                event.getEventTime(),
                event.getRepeatType(),
                event.isReminderEnabled(),
                event.getNotes(),
                event.getPhotoUrl(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    public static CalendarEventResponseDTO.CreateResult toCreateResult(CalendarEvent event) {
        return new CalendarEventResponseDTO.CreateResult(event.getEventId());
    }

    public static CalendarEventResponseDTO.DeleteResult toDeleteResult(CalendarEvent event) {
        return new CalendarEventResponseDTO.DeleteResult(event.getEventId());
    }

    public static CalendarEventResponseDTO.ReminderResult toReminderResult(CalendarEvent event) {
        return new CalendarEventResponseDTO.ReminderResult(event.getEventId(), event.isReminderEnabled());
    }

    public static CalendarEventResponseDTO.MedLogResult toMedLogResult(
            Long eventId,
            LocalDate date,
            boolean taken
    ) {
        return new CalendarEventResponseDTO.MedLogResult(eventId, date, taken);
    }
}
