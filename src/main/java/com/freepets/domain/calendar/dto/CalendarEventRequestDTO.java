package com.freepets.domain.calendar.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.freepets.domain.calendar.entity.CalendarEventType;
import com.freepets.domain.calendar.entity.RepeatType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

public class CalendarEventRequestDTO {

    private CalendarEventRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        // null이면 "전체" — 특정 반려동물이 아니라 이 유저의 모든 반려동물에 적용되는 일정.
        private Long petId;

        @NotNull(message = "일정 종류는 필수입니다.")
        private CalendarEventType eventType;

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
        private String title;

        // 반복 없음이면 유일한 발생일(기간 일정이면 시작일), 반복 있으면 발생 계산의 기준일(anchor)이다.
        @NotNull(message = "날짜는 필수입니다.")
        private LocalDate date;

        // 여행처럼 며칠에 걸치는 일정의 종료일 — 생략하면 date와 같은 날(기간 없음)로 저장된다.
        // 반복 일정(repeatType != NONE)에는 지정할 수 없다(CALENDAR4007).
        private LocalDate endDate;

        private LocalTime time;

        private RepeatType repeatType = RepeatType.NONE;

        private boolean reminderEnabled = false;

        @Size(max = 1000, message = "메모는 1000자 이하로 입력해주세요.")
        private String notes;

        private MultipartFile photo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {

        private Long petId;

        @NotNull(message = "일정 종류는 필수입니다.")
        private CalendarEventType eventType;

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
        private String title;

        @NotNull(message = "날짜는 필수입니다.")
        private LocalDate date;

        // CreateRequest.endDate와 같다.
        private LocalDate endDate;

        private LocalTime time;

        private RepeatType repeatType = RepeatType.NONE;

        private boolean reminderEnabled = false;

        @Size(max = 1000, message = "메모는 1000자 이하로 입력해주세요.")
        private String notes;

        // 새 파일이 오면 교체, 안 오면 기존 사진을 그대로 유지한다(Pet.profile 수정과 같은 방식).
        private MultipartFile photo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReminderRequest {

        // Boolean 래퍼로 받아 누락 시에도 400이 나게 한다(원시 boolean이면 누락 시 그냥 false로
        // 채워져 "일부러 끔"과 "안 보냄"을 구분할 수 없다).
        @NotNull(message = "reminderEnabled는 필수입니다.")
        private Boolean reminderEnabled;
    }
}
