package com.freepets.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.calendar.entity.CalendarEvent;
import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.calendar.entity.CalendarMedLog;
import com.freepets.domain.calendar.entity.RepeatType;
import com.freepets.domain.calendar.repository.CalendarEventRepository;
import com.freepets.domain.calendar.repository.CalendarMedLogRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CalendarMedLogCommandServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private CalendarMedLogRepository calendarMedLogRepository;

    @InjectMocks
    private CalendarMedLogCommandService calendarMedLogCommandService;

    private User owner;

    private CalendarEvent medEvent(LocalDate startDate) {
        owner = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(owner, "id", 1L);

        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .eventType(CalendarEventType.MED)
                .title("관절 영양제")
                .startDate(startDate)
                .repeatType(RepeatType.DAILY)
                .build();
        ReflectionTestUtils.setField(event, "eventId", 100L);
        return event;
    }

    @Test
    void markTaken_신규면_로그를_새로_저장한다() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 1));
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(calendarMedLogRepository.findByEvent_EventIdAndDate(100L, date)).thenReturn(Optional.empty());

        var result = calendarMedLogCommandService.markTaken(1L, 100L, date);

        assertThat(result.taken()).isTrue();
        verify(calendarMedLogRepository).save(any(CalendarMedLog.class));
    }

    @Test
    void markTaken_이미_체크된_상태면_다시_저장하지_않고_idempotent하게_성공한다() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 1));
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(calendarMedLogRepository.findByEvent_EventIdAndDate(100L, date))
                .thenReturn(Optional.of(CalendarMedLog.builder().event(event).date(date).build()));

        var result = calendarMedLogCommandService.markTaken(1L, 100L, date);

        assertThat(result.taken()).isTrue();
        verify(calendarMedLogRepository, never()).save(any());
    }

    @Test
    void markTaken_동시요청으로_유니크_제약_충돌해도_예외_없이_성공으로_처리한다() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 1));
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(calendarMedLogRepository.findByEvent_EventIdAndDate(100L, date)).thenReturn(Optional.empty());
        when(calendarMedLogRepository.save(any(CalendarMedLog.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        var result = calendarMedLogCommandService.markTaken(1L, 100L, date);

        assertThat(result.taken()).isTrue();
    }

    @Test
    void markTaken_MED_아닌_타입이면_CALENDAR4004_저장은_안_한다() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(event, "eventType", CalendarEventType.VACCINE);
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarMedLogCommandService.markTaken(1L, 100L, LocalDate.of(2026, 9, 10))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CALENDAR4004);
        verify(calendarMedLogRepository, never()).save(any());
    }

    @Test
    void markTaken_시작일_이전_날짜면_CALENDAR4005() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 10));

        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarMedLogCommandService.markTaken(1L, 100L, LocalDate.of(2026, 9, 9))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CALENDAR4005);
    }

    @Test
    void markTaken_타인_이벤트면_CALENDAR4002() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 1));
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarMedLogCommandService.markTaken(999L, 100L, LocalDate.of(2026, 9, 10))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CALENDAR4002);
    }

    @Test
    void unmarkTaken_존재하는_로그를_삭제한다() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 1));
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        var result = calendarMedLogCommandService.unmarkTaken(1L, 100L, date);

        assertThat(result.taken()).isFalse();
        verify(calendarMedLogRepository, times(1)).deleteByEvent_EventIdAndDate(100L, date);
    }

    @Test
    void unmarkTaken_이미_해제된_상태여도_idempotent하게_성공한다() {
        CalendarEvent event = medEvent(LocalDate.of(2026, 9, 1));
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        var result = calendarMedLogCommandService.unmarkTaken(1L, 100L, date);

        assertThat(result.taken()).isFalse();
    }
}
