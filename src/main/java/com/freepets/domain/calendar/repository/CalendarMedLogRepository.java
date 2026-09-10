package com.freepets.domain.calendar.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.calendar.entity.CalendarMedLog;

public interface CalendarMedLogRepository extends JpaRepository<CalendarMedLog, Long> {

    Optional<CalendarMedLog> findByEvent_EventIdAndDate(
            Long eventId,
            LocalDate date
    );

    // GET 목록 조회에서 MED 타입 occurrence들의 taken 여부를 한 번에 계산하려고 배치로 가져온다.
    List<CalendarMedLog> findAllByEvent_EventIdInAndDateBetween(
            List<Long> eventIds,
            LocalDate rangeStart,
            LocalDate rangeEnd
    );

    void deleteByEvent_EventIdAndDate(
            Long eventId,
            LocalDate date
    );
}
