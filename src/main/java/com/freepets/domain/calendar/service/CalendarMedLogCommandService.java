package com.freepets.domain.calendar.service;

import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
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
import lombok.extern.slf4j.Slf4j;

// 약 복용(MED) 일정의 날짜별 체크. CalendarEventCommandService와 소유권 검증 로직이 겹치지만
// 이 리포는 도메인 서비스마다 이런 소규모 헬퍼를 공유하지 않고 각자 복붙하는 컨벤션이라 그대로
// 따른다(PetCommandService/PetSatisfactionCommandService 관계와 동일).
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CalendarMedLogCommandService {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarMedLogRepository calendarMedLogRepository;

    public CalendarEventResponseDTO.MedLogResult markTaken(
            Long userId,
            Long eventId,
            LocalDate date
    ) {
        CalendarEvent event = findOwnedEvent(userId, eventId);
        validateMedLogTarget(event, date);

        if (calendarMedLogRepository.findByEvent_EventIdAndDate(eventId, date).isEmpty()) {
            save(event, date);
        }

        return CalendarEventConverter.toMedLogResult(eventId, date, true);
    }

    public CalendarEventResponseDTO.MedLogResult unmarkTaken(
            Long userId,
            Long eventId,
            LocalDate date
    ) {
        CalendarEvent event = findOwnedEvent(userId, eventId);
        validateMedLogTarget(event, date);

        calendarMedLogRepository.deleteByEvent_EventIdAndDate(eventId, date);

        return CalendarEventConverter.toMedLogResult(eventId, date, false);
    }

    // 존재 여부가 곧 체크 상태라 upsert가 필요한데, 같은 (event, date)로 거의 동시에 두 번
    // 요청이 오면(중복 탭 등) 유니크 제약을 위반할 수 있다. 값 차이가 없는 단순 존재-여부라
    // 이미 누군가 만든 거로 보고 성공으로 간주한다 — 에러를 올릴 이유가 없다.
    private void save(
            CalendarEvent event,
            LocalDate date
    ) {
        try {
            calendarMedLogRepository.save(CalendarMedLog.builder().event(event).date(date).build());
        } catch (DataIntegrityViolationException e) {
            log.warn("이미 존재하는 복용 체크입니다 — eventId={}, date={}", event.getEventId(), date);
        }
    }

    private void validateMedLogTarget(
            CalendarEvent event,
            LocalDate date
    ) {
        if (event.getEventType() != CalendarEventType.MED) {
            throw new GeneralException(ErrorStatus.CALENDAR4004);
        }
        if (date.isBefore(event.getStartDate())) {
            throw new GeneralException(ErrorStatus.CALENDAR4005);
        }
    }

    private CalendarEvent findOwnedEvent(
            Long userId,
            Long eventId
    ) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CALENDAR4001));

        if (!event.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.CALENDAR4002);
        }

        return event;
    }
}
