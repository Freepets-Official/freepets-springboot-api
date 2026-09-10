package com.freepets.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.calendar.dto.CalendarEventResponseDTO;
import com.freepets.domain.calendar.entity.CalendarEvent;
import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.calendar.entity.CalendarMedLog;
import com.freepets.domain.calendar.entity.RepeatType;
import com.freepets.domain.calendar.repository.CalendarEventRepository;
import com.freepets.domain.calendar.repository.CalendarMedLogRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CalendarEventQueryServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private CalendarMedLogRepository calendarMedLogRepository;

    @InjectMocks
    private CalendarEventQueryService calendarEventQueryService;

    private User owner() {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private CalendarEvent event(
            Long eventId,
            CalendarEventType type,
            LocalDate startDate,
            RepeatType repeatType,
            Pet pet
    ) {
        CalendarEvent event = CalendarEvent.builder()
                .user(owner())
                .pet(pet)
                .eventType(type)
                .title("일정")
                .startDate(startDate)
                .repeatType(repeatType)
                .build();
        ReflectionTestUtils.setField(event, "eventId", eventId);
        return event;
    }

    @Test
    void date와_month_둘다_없으면_CALENDAR4003() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarEventQueryService.getEvents(1L, null, null)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CALENDAR4003);
    }

    @Test
    void date와_month_둘다_있으면_CALENDAR4003() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarEventQueryService.getEvents(1L, LocalDate.of(2026, 9, 10), YearMonth.of(2026, 9))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CALENDAR4003);
    }

    @Test
    void 반복_일정을_월로_조회하면_날짜별로_여러_건_전개된다() {
        CalendarEvent daily = event(10L, CalendarEventType.CHECKUP, LocalDate.of(2026, 9, 26), RepeatType.DAILY, null);
        when(calendarEventRepository.findCandidatesByUser_Id(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(daily));

        var result = calendarEventQueryService.getEvents(1L, null, YearMonth.of(2026, 9));

        // 9/26 ~ 9/30, 5건 (eventId는 공유)
        assertThat(result.events()).hasSize(5);
        assertThat(result.events()).allMatch(occurrence -> occurrence.eventId().equals(10L));
        assertThat(result.events().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(result.events().get(4).date()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void MED_타입_occurrence는_로그_존재여부로_taken이_결정된다() {
        CalendarEvent med = event(20L, CalendarEventType.MED, LocalDate.of(2026, 9, 1), RepeatType.DAILY, null);
        when(calendarEventRepository.findCandidatesByUser_Id(1L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
                .thenReturn(List.of(med));
        when(calendarMedLogRepository.findAllByEvent_EventIdInAndDateBetween(
                List.of(20L), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
                .thenReturn(List.of(CalendarMedLog.builder().event(med).date(LocalDate.of(2026, 9, 10)).build()));

        var result = calendarEventQueryService.getEvents(1L, LocalDate.of(2026, 9, 10), null);

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).taken()).isTrue();
    }

    @Test
    void MED_타입인데_그날_로그가_없으면_taken_false() {
        CalendarEvent med = event(20L, CalendarEventType.MED, LocalDate.of(2026, 9, 1), RepeatType.DAILY, null);
        when(calendarEventRepository.findCandidatesByUser_Id(1L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
                .thenReturn(List.of(med));
        when(calendarMedLogRepository.findAllByEvent_EventIdInAndDateBetween(anyList(), any(), any()))
                .thenReturn(List.of());

        var result = calendarEventQueryService.getEvents(1L, LocalDate.of(2026, 9, 10), null);

        assertThat(result.events().get(0).taken()).isFalse();
    }

    @Test
    void 비MED_타입은_taken이_null이라_med로그_조회_자체를_안한다() {
        CalendarEvent vaccine = event(30L, CalendarEventType.VACCINE, LocalDate.of(2026, 9, 10), RepeatType.NONE, null);
        when(calendarEventRepository.findCandidatesByUser_Id(1L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
                .thenReturn(List.of(vaccine));

        var result = calendarEventQueryService.getEvents(1L, LocalDate.of(2026, 9, 10), null);

        assertThat(result.events().get(0).taken()).isNull();
        org.mockito.Mockito.verifyNoInteractions(calendarMedLogRepository);
    }

    @Test
    void 기간_일정은_한_건만_나오고_응답에_endDate가_채워진다() {
        CalendarEvent trip = CalendarEvent.builder()
                .user(owner())
                .eventType(CalendarEventType.TRAVEL)
                .title("강릉 여행")
                .startDate(LocalDate.of(2026, 9, 8))
                .endDate(LocalDate.of(2026, 9, 12))
                .repeatType(RepeatType.NONE)
                .build();
        ReflectionTestUtils.setField(trip, "eventId", 50L);
        when(calendarEventRepository.findCandidatesByUser_Id(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(trip));

        var result = calendarEventQueryService.getEvents(1L, null, YearMonth.of(2026, 9));

        assertThat(result.events()).hasSize(1);
        CalendarEventResponseDTO.EventOccurrence occurrence = result.events().get(0);
        assertThat(occurrence.date()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(occurrence.endDate()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    void 기간_없는_일정은_응답에_endDate가_없다() {
        CalendarEvent vaccine = event(30L, CalendarEventType.VACCINE, LocalDate.of(2026, 9, 10), RepeatType.NONE, null);
        when(calendarEventRepository.findCandidatesByUser_Id(1L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
                .thenReturn(List.of(vaccine));

        var result = calendarEventQueryService.getEvents(1L, LocalDate.of(2026, 9, 10), null);

        assertThat(result.events().get(0).endDate()).isNull();
    }

    @Test
    void pet이_있으면_petName이_채워지고_없으면_null이다() {
        Pet pet = Pet.builder()
                .user(owner())
                .name("댕댕이")
                .kind(Kind.DOG)
                .species("포메라니안")
                .weight(java.math.BigDecimal.valueOf(3.2))
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
                .build();
        ReflectionTestUtils.setField(pet, "petId", 7L);

        CalendarEvent withPet = event(40L, CalendarEventType.VACCINE, LocalDate.of(2026, 9, 10), RepeatType.NONE, pet);
        when(calendarEventRepository.findCandidatesByUser_Id(1L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
                .thenReturn(List.of(withPet));

        var result = calendarEventQueryService.getEvents(1L, LocalDate.of(2026, 9, 10), null);

        CalendarEventResponseDTO.EventOccurrence occurrence = result.events().get(0);
        assertThat(occurrence.petId()).isEqualTo(7L);
        assertThat(occurrence.petName()).isEqualTo("댕댕이");
    }
}
