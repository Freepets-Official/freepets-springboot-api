package com.freepets.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepets.domain.calendar.entity.RepeatType;

class CalendarOccurrenceCalculatorTest {

    // 이 파일의 기존 테스트는 전부 "기간 없음"(endDate == startDate) 시나리오라, endDate를
    // 매번 startDate로 채워 새 5-인자 시그니처에 위임한다. 기간(endDate) 자체를 검증하는
    // 테스트는 아래 별도 메서드에서 실제 endDate 값을 넘긴다.
    private List<LocalDate> occurrencesWithin(
            LocalDate startDate,
            RepeatType repeatType,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        return CalendarOccurrenceCalculator.occurrencesWithin(startDate, startDate, repeatType, rangeStart, rangeEnd);
    }

    @Test
    void NONE_시작일이_범위_안에_있으면_그_하루만_나온다() {
        LocalDate startDate = LocalDate.of(2026, 9, 10);

        assertThat(occurrencesWithin(startDate, RepeatType.NONE, startDate, startDate))
                .containsExactly(startDate);
        assertThat(occurrencesWithin(startDate, RepeatType.NONE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .containsExactly(startDate);
    }

    @Test
    void NONE_시작일이_범위_밖이면_안_나온다() {
        LocalDate startDate = LocalDate.of(2026, 9, 10);

        assertThat(occurrencesWithin(startDate, RepeatType.NONE,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 30)))
                .isEmpty();
        assertThat(occurrencesWithin(startDate, RepeatType.NONE,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 9)))
                .isEmpty();
    }

    @Test
    void DAILY_월_조회시_시작일부터_그달_끝까지_매일_나오고_시작일_이전은_안_나온다() {
        LocalDate startDate = LocalDate.of(2026, 9, 10);

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.DAILY,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(occurrences).hasSize(21); // 9/10 ~ 9/30
        assertThat(occurrences.get(0)).isEqualTo(startDate);
        assertThat(occurrences.get(occurrences.size() - 1)).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(occurrences).doesNotContain(LocalDate.of(2026, 9, 9));
    }

    @Test
    void DAILY_단일_날짜_경계값() {
        LocalDate startDate = LocalDate.of(2026, 9, 10);

        assertThat(occurrencesWithin(startDate, RepeatType.DAILY, startDate, startDate))
                .containsExactly(startDate);
        assertThat(occurrencesWithin(startDate, RepeatType.DAILY,
                startDate.minusDays(1), startDate.minusDays(1)))
                .isEmpty();
    }

    @Test
    void WEEKLY_앵커와_다른_요일만_조회하면_빈_리스트() {
        LocalDate thursday = LocalDate.of(2026, 9, 10); // 목요일

        List<LocalDate> occurrences = occurrencesWithin(thursday, RepeatType.WEEKLY,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 16)); // 금~수, 다음 목(9/17) 전까지

        assertThat(occurrences).isEmpty();
    }

    @Test
    void WEEKLY_한달_조회시_같은_요일이_매주_나온다() {
        LocalDate thursday = LocalDate.of(2026, 9, 3); // 목요일

        List<LocalDate> occurrences = occurrencesWithin(thursday, RepeatType.WEEKLY,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(occurrences).containsExactly(
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 24)
        );
    }

    @Test
    void WEEKLY_앵커보다_이전_달을_조회하면_빈_리스트() {
        LocalDate startDate = LocalDate.of(2026, 10, 1); // 다음달 앵커

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.WEEKLY,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(occurrences).isEmpty();
    }

    @Test
    void MONTHLY_31일_앵커는_평년_2월에_28일로_클램핑된다() {
        LocalDate startDate = LocalDate.of(2026, 1, 31); // 2026은 평년

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.MONTHLY,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(occurrences).containsExactly(LocalDate.of(2026, 2, 28));
    }

    @Test
    void MONTHLY_31일_앵커는_윤년_2월에_29일로_클램핑된다() {
        LocalDate startDate = LocalDate.of(2027, 1, 31);

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.MONTHLY,
                LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)); // 2028은 윤년

        assertThat(occurrences).containsExactly(LocalDate.of(2028, 2, 29));
    }

    @Test
    void MONTHLY_30일_앵커는_31일짜리_달에서_31일로_밀리지_않고_30일_그대로다() {
        LocalDate startDate = LocalDate.of(2026, 4, 30);

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.MONTHLY,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(occurrences).containsExactly(LocalDate.of(2026, 5, 30));
    }

    @Test
    void MONTHLY_클램핑_없이_같은_날짜가_정확히_매칭되는_경우() {
        LocalDate startDate = LocalDate.of(2026, 9, 15);

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.MONTHLY,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(occurrences).containsExactly(LocalDate.of(2026, 10, 15));
    }

    @Test
    void MONTHLY_앵커보다_이전_달을_조회하면_빈_리스트() {
        LocalDate startDate = LocalDate.of(2026, 9, 15);

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.MONTHLY,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(occurrences).isEmpty();
    }

    @Test
    void NONE_기간_일정은_시작일이_범위보다_과거여도_끝나는_날이_범위와_겹치면_나온다() {
        // 5/28~6/2 여행을 6월(6/1~6/30)로 조회하면, 시작일(5/28)은 범위 밖이지만 끝나는 날(6/2)이
        // 범위 안이라 발생일(대표값=시작일)이 나와야 한다.
        LocalDate startDate = LocalDate.of(2026, 5, 28);
        LocalDate endDate = LocalDate.of(2026, 6, 2);

        List<LocalDate> occurrences = CalendarOccurrenceCalculator.occurrencesWithin(
                startDate, endDate, RepeatType.NONE, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)
        );

        assertThat(occurrences).containsExactly(startDate);
    }

    @Test
    void NONE_기간_일정이_조회_범위와_전혀_안_겹치면_안_나온다() {
        LocalDate startDate = LocalDate.of(2026, 5, 1);
        LocalDate endDate = LocalDate.of(2026, 5, 3);

        List<LocalDate> occurrences = CalendarOccurrenceCalculator.occurrencesWithin(
                startDate, endDate, RepeatType.NONE, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)
        );

        assertThat(occurrences).isEmpty();
    }

    @Test
    void NONE_기간_일정이_조회_범위를_완전히_감싸도_한_번만_나온다() {
        // 1/1~12/31처럼 조회 월을 완전히 감싸는 기간이어도, 날짜별로 쪼개지 않고 대표값 1건만 낸다.
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 12, 31);

        List<LocalDate> occurrences = CalendarOccurrenceCalculator.occurrencesWithin(
                startDate, endDate, RepeatType.NONE, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)
        );

        assertThat(occurrences).containsExactly(startDate);
    }

    @Test
    void MONTHLY_조회_범위가_그달_며칠_이후부터면_클램핑된_날짜가_범위_전이면_빠진다() {
        // 1/31 앵커 → 2월은 28일로 클램핑되는데, 조회 범위를 2/1~2/20으로 좁히면
        // 클램핑된 발생일(2/28)이 범위 밖이라 안 나와야 한다.
        LocalDate startDate = LocalDate.of(2026, 1, 31);

        List<LocalDate> occurrences = occurrencesWithin(startDate, RepeatType.MONTHLY,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 20));

        assertThat(occurrences).isEmpty();
    }
}
