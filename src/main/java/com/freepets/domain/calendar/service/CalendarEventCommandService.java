package com.freepets.domain.calendar.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
import com.freepets.infra.s3.S3ImageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CalendarEventCommandService {

    private final CalendarEventRepository calendarEventRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final S3ImageService s3ImageService;

    public CalendarEventResponseDTO.CreateResult createEvent(
            Long userId,
            CalendarEventRequestDTO.CreateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        Pet pet = resolvePet(userId, request.getPetId());
        String photoUrl = uploadPhotoIfPresent(request.getPhoto());

        CalendarEvent event = CalendarEventConverter.toEvent(request, user, pet, photoUrl);
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

        String previousPhotoUrl = event.getPhotoUrl();
        String photoUrl = isNewPhotoPresent(request.getPhoto())
                ? uploadPhotoIfPresent(request.getPhoto())
                : previousPhotoUrl;

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
                request.getNotes(),
                photoUrl
        );

        if (isNewPhotoPresent(request.getPhoto()) && previousPhotoUrl != null) {
            s3ImageService.delete(previousPhotoUrl);
        }

        return CalendarEventConverter.toEventDetail(event);
    }

    public CalendarEventResponseDTO.DeleteResult deleteEvent(
            Long userId,
            Long eventId
    ) {
        CalendarEvent event = findOwnedEvent(userId, eventId);
        calendarEventRepository.delete(event);

        // Pet/Review는 소프트 삭제라 사진을 지우지 않고 남겨두지만(나중에 정리할 여지가 있음),
        // 캘린더 일정은 하드 삭제라 이 행을 지우고 나면 photoUrl을 되찾을 방법이 없다 — 지금
        // 지우지 않으면 영원히 못 지우는 고아 파일이 되므로, 여기서만 예외적으로 삭제한다.
        if (event.getPhotoUrl() != null) {
            s3ImageService.delete(event.getPhotoUrl());
        }

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

    private boolean isNewPhotoPresent(MultipartFile photo) {
        return photo != null && !photo.isEmpty();
    }

    private String uploadPhotoIfPresent(MultipartFile photo) {
        if (!isNewPhotoPresent(photo)) {
            return null;
        }

        return s3ImageService.upload(photo);
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
