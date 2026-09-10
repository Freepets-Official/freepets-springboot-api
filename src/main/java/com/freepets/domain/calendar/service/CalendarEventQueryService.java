package com.freepets.domain.calendar.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.calendar.converter.CalendarEventConverter;
import com.freepets.domain.calendar.dto.CalendarEventResponseDTO;
import com.freepets.domain.calendar.entity.CalendarEvent;
import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.calendar.entity.CalendarMedLog;
import com.freepets.domain.calendar.repository.CalendarEventRepository;
import com.freepets.domain.calendar.repository.CalendarMedLogRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventQueryService {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarMedLogRepository calendarMedLogRepository;

    // GET /api/v1/calendar-events?date= 또는 ?month= — 그 날짜/월에 실제로 발생하는 일정을
    // 반복 규칙을 풀어서 내려준다. 반복 일정은 조건에 맞는 날짜마다 한 건씩(eventId는 공유,
    // date는 발생일) 나온다.
    public CalendarEventResponseDTO.EventList getEvents(
            Long userId,
            LocalDate date,
            YearMonth month
    ) {
        LocalDate rangeStart;
        LocalDate rangeEnd;

        if (date != null && month == null) {
            rangeStart = date;
            rangeEnd = date;
        } else if (month != null && date == null) {
            rangeStart = month.atDay(1);
            rangeEnd = month.atEndOfMonth();
        } else {
            throw new GeneralException(ErrorStatus.CALENDAR4003);
        }

        List<CalendarEvent> candidates = calendarEventRepository.findCandidatesByUser_Id(userId, rangeStart, rangeEnd);
        Set<String> takenKeys = takenKeysOf(candidates, rangeStart, rangeEnd);

        List<CalendarEventResponseDTO.EventOccurrence> occurrences = candidates.stream()
                .flatMap(event -> CalendarOccurrenceCalculator
                        .occurrencesWithin(event.getStartDate(), event.getRepeatType(), rangeStart, rangeEnd)
                        .stream()
                        .map(occurrenceDate -> CalendarEventConverter.toEventOccurrence(
                                event,
                                occurrenceDate,
                                takenKeys.contains(medLogKey(event.getEventId(), occurrenceDate))
                        )))
                .sorted(Comparator
                        .comparing(CalendarEventResponseDTO.EventOccurrence::date)
                        .thenComparing(
                                CalendarEventResponseDTO.EventOccurrence::time,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(CalendarEventResponseDTO.EventOccurrence::eventId))
                .toList();

        return CalendarEventConverter.toEventList(occurrences);
    }

    // MED 타입 후보들의 (eventId, date) 조합을 한 번에 조회해 Set으로 만든다 — occurrence마다
    // 따로 조회하면 N+1이 난다.
    private Set<String> takenKeysOf(
            List<CalendarEvent> candidates,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        List<Long> medEventIds = candidates.stream()
                .filter(event -> event.getEventType() == CalendarEventType.MED)
                .map(CalendarEvent::getEventId)
                .toList();

        if (medEventIds.isEmpty()) {
            return Set.of();
        }

        List<CalendarMedLog> medLogs = calendarMedLogRepository
                .findAllByEvent_EventIdInAndDateBetween(medEventIds, rangeStart, rangeEnd);

        Set<String> keys = new HashSet<>();
        for (CalendarMedLog medLog : medLogs) {
            keys.add(medLogKey(medLog.getEvent().getEventId(), medLog.getDate()));
        }
        return keys;
    }

    private String medLogKey(
            Long eventId,
            LocalDate date
    ) {
        return eventId + ":" + date;
    }
}
