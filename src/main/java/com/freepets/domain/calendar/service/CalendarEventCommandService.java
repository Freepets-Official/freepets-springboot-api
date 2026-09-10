package com.freepets.domain.calendar.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.calendar.converter.CalendarEventConverter;
import com.freepets.domain.calendar.dto.CalendarEventRequestDTO;
import com.freepets.domain.calendar.dto.CalendarEventResponseDTO;
import com.freepets.domain.calendar.entity.CalendarEvent;
import com.freepets.domain.calendar.repository.CalendarEventRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CalendarEventCommandService {

    private final CalendarEventRepository calendarEventRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;

    public CalendarEventResponseDTO.CreateResult createEvent(
            Long userId,
            CalendarEventRequestDTO.CreateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        Pet pet = resolvePet(userId, request.getPetId());

        CalendarEvent event = CalendarEventConverter.toEvent(request, user, pet);
        CalendarEvent savedEvent = calendarEventRepository.save(event);

        return CalendarEventConverter.toCreateResult(savedEvent);
    }

    public CalendarEventResponseDTO.EventDetail updateEvent(
            Long userId,
            Long eventId,
            CalendarEventRequestDTO.UpdateRequest request
    ) {
        CalendarEvent event = findOwnedEvent(userId, eventId);
        Pet pet = resolvePet(userId, request.getPetId());

        // 알려진 한계: eventType을 MED에서 다른 타입으로 바꾸거나 반복 규칙/시작일을 바꾸면,
        // 예전 스케줄 기준으로 쌓인 CalendarMedLog 행은 지우지 않고 그대로 둔다. 이후 조회에서
        // 그 날짜가 더 이상 발생일로 계산되지 않으면 그냥 안 보일 뿐이라 틀린 값이 노출되진
        // 않지만, 데이터가 지저분하게 남는다 — eventType 변경이 흔한 흐름이 아니라 지금은
        // 정리 로직을 두지 않았다.
        event.update(
                pet,
                request.getEventType(),
                request.getTitle(),
                request.getDate(),
                request.getTime(),
                request.getRepeatType(),
                request.isReminderEnabled(),
                request.getNotes()
        );

        return CalendarEventConverter.toEventDetail(event);
    }

    public CalendarEventResponseDTO.DeleteResult deleteEvent(
            Long userId,
            Long eventId
    ) {
        CalendarEvent event = findOwnedEvent(userId, eventId);
        calendarEventRepository.delete(event);

        return CalendarEventConverter.toDeleteResult(event);
    }

    public CalendarEventResponseDTO.ReminderResult updateReminder(
            Long userId,
            Long eventId,
            CalendarEventRequestDTO.ReminderRequest request
    ) {
        CalendarEvent event = findOwnedEvent(userId, eventId);
        event.toggleReminder(request.getReminderEnabled());

        return CalendarEventConverter.toReminderResult(event);
    }

    // null이면 "전체"(petId 미지정) — 그대로 null을 돌려준다. 지정됐으면 존재·본인 소유
    // 여부를 확인한다(PetCommandService.findOwnedPet과 같은 검증, PET4001/PET4002 재사용).
    private Pet resolvePet(
            Long userId,
            Long petId
    ) {
        if (petId == null) {
            return null;
        }

        Pet pet = petRepository.findByPetIdAndDeletedAtIsNull(petId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PET4001));

        if (!pet.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.PET4002);
        }

        return pet;
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
