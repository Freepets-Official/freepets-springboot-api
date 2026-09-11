package com.freepets.domain.gamification.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class GamificationRequestDTO {

    private GamificationRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NotificationRequest {

        // CalendarEventRequestDTO.ReminderRequest와 같은 이유로 Boolean 래퍼 — 누락 시에도
        // 400이 나야 "일부러 끔"과 "안 보냄"이 섞이지 않는다.
        @NotNull(message = "levelUpNotificationEnabled는 필수입니다.")
        private Boolean levelUpNotificationEnabled;
    }
}
