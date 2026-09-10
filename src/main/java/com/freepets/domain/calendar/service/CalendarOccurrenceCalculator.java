package com.freepets.domain.calendar.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.calendar.entity.RepeatType;

/**
 * 반복 규칙(startDate + repeatType)에서 조회 범위 [rangeStart, rangeEnd] 안에 실제로 발생하는
 * 날짜들을 계산한다. Spring 빈이 아니라 순수 정적 유틸리티다 — 요일/말일 클램핑 계산을 SQL로
 * 포터블하게 표현하기 어렵고, 순수 함수로 두면 Mockito 없이 바로 단위 테스트할 수 있다.
 *
 * <p>{@code CalendarEventRepository}가 후보 이벤트를 넓게 걸러오고, 실제 발생일 판정은 여기서
 * 한다({@code CalendarEventQueryService} 참고).
 */
public final class CalendarOccurrenceCalculator {

    private CalendarOccurrenceCalculator() {}

    public static List<LocalDate> occurrencesWithin(
            LocalDate startDate,
            LocalDate endDate,
            RepeatType repeatType,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        // 반복 종류를 안 가리고, 시작일(anchor) 이후에만 발생할 수 있다.
        if (rangeEnd.isBefore(startDate)) {
            return List.of();
        }

        return switch (repeatType) {
            // endDate만 기간을 쓴다 — 반복 일정엔 기간 개념이 없어(CalendarEventCommandService가
            // 생성/수정 시점에 막는다) DAILY/WEEKLY/MONTHLY는 endDate를 아예 안 받는다.
            case NONE -> occursOnce(startDate, endDate, rangeStart, rangeEnd);
            case DAILY -> dailyOccurrences(startDate, rangeStart, rangeEnd);
            case WEEKLY -> weeklyOccurrences(startDate, rangeStart, rangeEnd);
            case MONTHLY -> monthlyOccurrences(startDate, rangeStart, rangeEnd);
        };
    }

    // 반복 없음 — [startDate, endDate] 기간이 조회 범위와 겹치기만 하면 발생한 것으로 본다.
    // 여러 날에 걸친 기간 일정이라도 발생일은 항상 startDate 하나로 대표한다(날짜별로 쪼개
    // 여러 건을 만들지 않는다) — 응답의 endDate 필드로 프론트가 기간 전체를 그린다.
    private static List<LocalDate> occursOnce(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        boolean overlaps = !startDate.isAfter(rangeEnd) && !endDate.isBefore(rangeStart);
        return overlaps ? List.of(startDate) : List.of();
    }

    private static List<LocalDate> dailyOccurrences(
            LocalDate startDate,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        LocalDate effectiveStart = laterOf(startDate, rangeStart);
        if (effectiveStart.isAfter(rangeEnd)) {
            return List.of();
        }

        List<LocalDate> occurrences = new ArrayList<>();
        for (LocalDate date = effectiveStart; !date.isAfter(rangeEnd); date = date.plusDays(1)) {
            occurrences.add(date);
        }
        return occurrences;
    }

    // startDate와 같은 요일만, startDate 이후부터 7일 간격으로.
    private static List<LocalDate> weeklyOccurrences(
            LocalDate startDate,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        LocalDate effectiveStart = laterOf(startDate, rangeStart);
        if (effectiveStart.isAfter(rangeEnd)) {
            return List.of();
        }

        DayOfWeek anchorDayOfWeek = startDate.getDayOfWeek();
        LocalDate first = effectiveStart;
        while (first.getDayOfWeek() != anchorDayOfWeek) {
            first = first.plusDays(1);
        }

        List<LocalDate> occurrences = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(rangeEnd); date = date.plusWeeks(1)) {
            occurrences.add(date);
        }
        return occurrences;
    }

    // startDate의 "며칠"을 매달 반복하되, 그 달에 그 날짜가 없으면(예: 31일 앵커의 2월) 그 달의
    // 마지막 날로 클램핑한다 — 다음 달로 밀거나 건너뛰지 않는다. 이 규칙을 기본값으로 채택했다.
    private static List<LocalDate> monthlyOccurrences(
            LocalDate startDate,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        LocalDate effectiveStart = laterOf(startDate, rangeStart);
        if (effectiveStart.isAfter(rangeEnd)) {
            return List.of();
        }

        int anchorDayOfMonth = startDate.getDayOfMonth();
        List<LocalDate> occurrences = new ArrayList<>();

        YearMonth month = YearMonth.from(effectiveStart);
        YearMonth lastMonth = YearMonth.from(rangeEnd);
        while (!month.isAfter(lastMonth)) {
            LocalDate occurrence = clampedDayOfMonth(month, anchorDayOfMonth);
            if (!occurrence.isBefore(effectiveStart) && !occurrence.isAfter(rangeEnd)) {
                occurrences.add(occurrence);
            }
            month = month.plusMonths(1);
        }
        return occurrences;
    }

    private static LocalDate clampedDayOfMonth(
            YearMonth yearMonth,
            int anchorDayOfMonth
    ) {
        int day = Math.min(anchorDayOfMonth, yearMonth.lengthOfMonth());
        return yearMonth.atDay(day);
    }

    private static LocalDate laterOf(
            LocalDate a,
            LocalDate b
    ) {
        return a.isAfter(b) ? a : b;
    }
}
