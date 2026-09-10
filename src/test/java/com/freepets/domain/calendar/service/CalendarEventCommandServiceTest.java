package com.freepets.domain.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.calendar.dto.CalendarEventRequestDTO;
import com.freepets.domain.calendar.entity.CalendarEvent;
import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.calendar.entity.RepeatType;
import com.freepets.domain.calendar.repository.CalendarEventRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.s3.S3ImageService;

@ExtendWith(MockitoExtension.class)
class CalendarEventCommandServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3ImageService s3ImageService;

    @InjectMocks
    private CalendarEventCommandService calendarEventCommandService;

    private User user(Long id) {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Pet pet(
            Long petId,
            User owner
    ) {
        Pet pet = Pet.builder()
                .user(owner)
                .name("댕댕이")
                .kind(Kind.DOG)
                .species("포메라니안")
                .weight(java.math.BigDecimal.valueOf(3.2))
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private CalendarEventRequestDTO.CreateRequest createRequest(Long petId) {
        CalendarEventRequestDTO.CreateRequest request = new CalendarEventRequestDTO.CreateRequest();
        request.setPetId(petId);
        request.setEventType(CalendarEventType.VACCINE);
        request.setTitle("종합백신 2차");
        request.setDate(LocalDate.of(2026, 9, 10));
        request.setRepeatType(RepeatType.NONE);
        request.setReminderEnabled(true);
        return request;
    }

    @Test
    void createEvent_petId가_없으면_전체_적용으로_저장되고_petRepository는_호출하지_않는다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> {
            CalendarEvent saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "eventId", 100L);
            return saved;
        });

        var result = calendarEventCommandService.createEvent(1L, createRequest(null));

        assertThat(result.eventId()).isEqualTo(100L);
        verifyNoInteractions(petRepository);
    }

    @Test
    void createEvent_본인_petId면_정상_저장된다() {
        User owner = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(petRepository.findByPetIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(pet(7L, owner)));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> {
            CalendarEvent saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "eventId", 100L);
            return saved;
        });

        var result = calendarEventCommandService.createEvent(1L, createRequest(7L));

        assertThat(result.eventId()).isEqualTo(100L);
    }

    @Test
    void createEvent_타인_petId면_PET4002_저장은_안_한다() {
        User owner = user(1L);
        User other = user(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(petRepository.findByPetIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(pet(7L, other)));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarEventCommandService.createEvent(1L, createRequest(7L))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.PET4002);
        verify(calendarEventRepository, never()).save(any());
    }

    @Test
    void createEvent_존재하지_않는_petId면_PET4001() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(petRepository.findByPetIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarEventCommandService.createEvent(1L, createRequest(7L))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.PET4001);
        verify(calendarEventRepository, never()).save(any());
    }

    @Test
    void createEvent_존재하지_않는_유저면_MEMBER4005() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarEventCommandService.createEvent(1L, createRequest(null))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
        verifyNoInteractions(calendarEventRepository);
    }

    @Test
    void updateEvent_petId를_있음에서_전체로_바꿀_수_있다() {
        User owner = user(1L);
        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .pet(pet(7L, owner))
                .eventType(CalendarEventType.VACCINE)
                .title("기존 제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .reminderEnabled(false)
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        var result = calendarEventCommandService.updateEvent(1L, 100L, updateRequest(null));

        assertThat(result.petId()).isNull();
    }

    @Test
    void updateEvent_존재하지_않는_이벤트면_CALENDAR4001() {
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarEventCommandService.updateEvent(1L, 100L, updateRequest(null))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CALENDAR4001);
    }

    @Test
    void updateEvent_타인_이벤트면_CALENDAR4002() {
        CalendarEvent event = CalendarEvent.builder()
                .user(user(2L))
                .eventType(CalendarEventType.VACCINE)
                .title("기존 제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> calendarEventCommandService.updateEvent(1L, 100L, updateRequest(null))
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.CALENDAR4002);
    }

    private CalendarEventRequestDTO.UpdateRequest updateRequest(Long petId) {
        CalendarEventRequestDTO.UpdateRequest request = new CalendarEventRequestDTO.UpdateRequest();
        request.setPetId(petId);
        request.setEventType(CalendarEventType.VACCINE);
        request.setTitle("수정된 제목");
        request.setDate(LocalDate.of(2026, 9, 10));
        request.setRepeatType(RepeatType.NONE);
        request.setReminderEnabled(true);
        return request;
    }

    @Test
    void deleteEvent_정상_삭제된다() {
        User owner = user(1L);
        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .eventType(CalendarEventType.VACCINE)
                .title("제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        calendarEventCommandService.deleteEvent(1L, 100L);

        verify(calendarEventRepository).delete(event);
    }

    @Test
    void deleteEvent_타인_이벤트면_CALENDAR4002_삭제는_안_한다() {
        CalendarEvent event = CalendarEvent.builder()
                .user(user(2L))
                .eventType(CalendarEventType.VACCINE)
                .title("제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        assertThrows(GeneralException.class, () -> calendarEventCommandService.deleteEvent(1L, 100L));
        verify(calendarEventRepository, never()).delete(any());
    }

    @Test
    void deleteEvent_사진이_있으면_S3에서도_지운다() {
        User owner = user(1L);
        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .eventType(CalendarEventType.TRAVEL)
                .title("강릉 여행")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .photoUrl("https://s3-url/trip.jpg")
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        calendarEventCommandService.deleteEvent(1L, 100L);

        verify(s3ImageService).delete("https://s3-url/trip.jpg");
    }

    @Test
    void deleteEvent_사진이_없으면_S3_삭제를_호출하지_않는다() {
        User owner = user(1L);
        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .eventType(CalendarEventType.VACCINE)
                .title("제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        calendarEventCommandService.deleteEvent(1L, 100L);

        verify(s3ImageService, never()).delete(any());
    }

    @Test
    void createEvent_사진이_있으면_업로드해서_photoUrl로_저장된다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> {
            CalendarEvent saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "eventId", 100L);
            return saved;
        });
        MockMultipartFile photo = new MockMultipartFile("photo", "trip.jpg", "image/jpeg", "content".getBytes());
        when(s3ImageService.upload(photo)).thenReturn("https://s3-url/trip.jpg");

        CalendarEventRequestDTO.CreateRequest request = createRequest(null);
        request.setPhoto(photo);

        calendarEventCommandService.createEvent(1L, request);

        org.mockito.ArgumentCaptor<CalendarEvent> captor = org.mockito.ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());
        assertThat(captor.getValue().getPhotoUrl()).isEqualTo("https://s3-url/trip.jpg");
    }

    @Test
    void updateEvent_새_사진이_오면_기존_사진을_지우고_교체한다() {
        User owner = user(1L);
        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .eventType(CalendarEventType.TRAVEL)
                .title("기존 제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .photoUrl("https://s3-url/old.jpg")
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));
        MockMultipartFile newPhoto = new MockMultipartFile("photo", "new.jpg", "image/jpeg", "content".getBytes());
        when(s3ImageService.upload(newPhoto)).thenReturn("https://s3-url/new.jpg");

        CalendarEventRequestDTO.UpdateRequest request = updateRequest(null);
        request.setPhoto(newPhoto);

        var result = calendarEventCommandService.updateEvent(1L, 100L, request);

        assertThat(result.photoUrl()).isEqualTo("https://s3-url/new.jpg");
        verify(s3ImageService).delete("https://s3-url/old.jpg");
    }

    @Test
    void updateEvent_사진_없이_수정하면_기존_사진을_유지한다() {
        User owner = user(1L);
        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .eventType(CalendarEventType.TRAVEL)
                .title("기존 제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .photoUrl("https://s3-url/old.jpg")
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        var result = calendarEventCommandService.updateEvent(1L, 100L, updateRequest(null));

        assertThat(result.photoUrl()).isEqualTo("https://s3-url/old.jpg");
        verify(s3ImageService, never()).delete(any());
    }

    @Test
    void updateReminder_토글이_양방향으로_동작한다() {
        User owner = user(1L);
        CalendarEvent event = CalendarEvent.builder()
                .user(owner)
                .eventType(CalendarEventType.VACCINE)
                .title("제목")
                .startDate(LocalDate.of(2026, 9, 1))
                .repeatType(RepeatType.NONE)
                .reminderEnabled(false)
                .build();
        when(calendarEventRepository.findById(100L)).thenReturn(Optional.of(event));

        var onRequest = new CalendarEventRequestDTO.ReminderRequest();
        onRequest.setReminderEnabled(true);
        var onResult = calendarEventCommandService.updateReminder(1L, 100L, onRequest);
        assertThat(onResult.reminderEnabled()).isTrue();

        var offRequest = new CalendarEventRequestDTO.ReminderRequest();
        offRequest.setReminderEnabled(false);
        var offResult = calendarEventCommandService.updateReminder(1L, 100L, offRequest);
        assertThat(offResult.reminderEnabled()).isFalse();
    }
}
