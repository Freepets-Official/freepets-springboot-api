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
    // 후보에 넣고, NONE은 [startDate, endDate] 구간이 조회 범위와 겹칠 때만 좁힌다(기간 일정은
    // startDate가 범위보다 과거여도 endDate가 범위 안으로 걸칠 수 있다) — 실제 발생일 판정은
    // CalendarOccurrenceCalculator가 한다. endDate에 COALESCE를 쓰는 이유는 이 컬럼이 nullable이라서다
    // (db/pending-manual-migrations.sql 백필 전 레거시 행은 null일 수 있고, 그때는 startDate와
    // 같은 것으로 취급해야 CalendarEvent.getEndDate()의 애플리케이션 계층 기본값과 일치한다).
    // pet은 LEFT JOIN FETCH로 같이 가져와 지연 로딩 N+1을 막는다(pet이 null일 수 있어 INNER가 아니라 LEFT).
    @Query("""
            SELECT ce FROM CalendarEvent ce
            LEFT JOIN FETCH ce.pet
            WHERE ce.user.id = :userId
              AND ce.startDate <= :rangeEnd
              AND (ce.repeatType <> com.freepets.domain.calendar.entity.RepeatType.NONE
                   OR COALESCE(ce.endDate, ce.startDate) >= :rangeStart)
            """)
    List<CalendarEvent> findCandidatesByUser_Id(
            @Param("userId") Long userId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );
}
