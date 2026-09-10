package com.freepets.domain.calendar.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.calendar.entity.CalendarEvent;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    // 조회 범위(rangeStart~rangeEnd)에서 실제로 발생할 수 있는 이벤트 후보를 넓게 걸러온다.
    // 반복 이벤트(NONE 아님)는 시작일이 조회 범위보다 과거여도 여전히 발생할 수 있어 무조건
    // 후보에 넣고, NONE은 시작일이 범위 안에 정확히 있을 때만 좁힌다 — 실제 발생일 판정은
    // CalendarOccurrenceCalculator가 한다. pet은 LEFT JOIN FETCH로 같이 가져와 지연 로딩
    // N+1을 막는다(pet이 null일 수 있어 INNER가 아니라 LEFT).
    @Query("""
            SELECT ce FROM CalendarEvent ce
            LEFT JOIN FETCH ce.pet
            WHERE ce.user.id = :userId
              AND ce.startDate <= :rangeEnd
              AND (ce.repeatType <> com.freepets.domain.calendar.entity.RepeatType.NONE OR ce.startDate >= :rangeStart)
            """)
    List<CalendarEvent> findCandidatesByUser_Id(
            @Param("userId") Long userId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );
}
