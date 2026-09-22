package com.freepets.domain.course.dto;

import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class CourseRequestDTO {

    private CourseRequestDTO() {}

    // POST /api/v1/courses, PUT /api/v1/courses/{courseId} 공용 — 직접 만들기(CUSTOM).
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SaveRequest {

        @NotBlank(message = "코스 이름을 입력해주세요.")
        private String name;

        private String description;

        // 10 = CourseAssemblyService.MAX_CUSTOM_STOPS와 맞춘다 — 스톱이 늘어날수록 무거워지는
        // 판별(POST /ai/course-check)·순서 최적화 요청 크기를 제한한다.
        // 원소에 @NotNull을 직접 건다 — 검증기는 리스트 안의 null 원소를 건너뛰므로, 이게 없으면
        // stops: [null]이 위 제약을 전부 통과한 뒤 서비스에서 NPE(500)가 된다.
        @NotEmpty(message = "코스에 시설을 1곳 이상 담아주세요.")
        @Size(max = 10, message = "코스는 최대 10곳까지만 담을 수 있습니다.")
        private List<@NotNull(message = "코스에 담을 시설을 선택해주세요.") @Valid StopRequest> stops;

        // 다른 사용자의 "둘러보기" 목록(GET /courses/public)에 노출할지. 기본값 false(비공개).
        // Lombok이 만드는 setPublic으로는 JSON 프로퍼티명이 public으로 깎여 응답(isPublic)과
        // 어긋나므로, PetRequestDTO.isVaccinated와 같은 이유로 setter를 직접 선언해 고정한다.
        @JsonProperty("isPublic")
        @Setter(AccessLevel.NONE)
        private boolean isPublic;

        public void setIsPublic(boolean isPublic) {
            this.isPublic = isPublic;
        }
    }

    // SaveRequest의 스톱 하나 — 어느 시설에 몇 시에 가는지. 순서는 배열 순서 그대로다.
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StopRequest {

        @NotNull(message = "코스에 담을 시설을 선택해주세요.")
        private Long facilityId;

        /**
         * 이 스톱에 도착하는 시각. 사용자가 아직 안 정했으면 비워서 보낸다 — 시간을 안 쓰는
         * 코스도 그대로 저장된다.
         *
         * <p>형식을 고정하지 않아 ISO 기본 파서가 받는다 — {@code "14:30"}도 {@code "14:30:00"}도
         * 된다. 패턴을 박으면 파싱까지 엄격해져서, 모바일 날짜 라이브러리가 흔히 내보내는
         * {@code LocalTime.toString()} 형식이 400으로 튕긴다(캘린더 API도 ISO를 받는다).
         * 응답은 {@code CourseResponseDTO.Stop}에서 {@code "HH:mm"}으로 고정해 내려간다.
         */
        private LocalTime visitTime;
    }

    // POST /api/v1/courses/optimize-order 전용 — 저장은 안 하고 스톱 순서만 최근접 이웃 방식으로
    // 다듬어본다(직접 검색해서 추가한 스톱까지 포함해 동선을 정리하고 싶을 때).
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OptimizeOrderRequest {

        // SaveRequest.stops와 같은 이유(CourseAssemblyService.MAX_CUSTOM_STOPS).
        @NotEmpty(message = "코스에 시설을 1곳 이상 담아주세요.")
        @Size(max = 10, message = "코스는 최대 10곳까지만 담을 수 있습니다.")
        private List<Long> stopIds;
    }

    // PUT /api/v1/courses/{courseId}/stops/{stopOrder} 전용 — 그 자리의 스톱만 다른 시설로 교체.
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReplaceStopRequest {

        @NotNull(message = "교체할 시설을 선택해주세요.")
        private Long facilityId;
    }

    // PATCH /api/v1/courses/{courseId}/visibility 전용 — 공개 여부만 바꾼다. SaveRequest(PUT)는
    // name·stops 전체를 요구해서 "공개 토글"만 하려는 클라이언트에도 코스 전체를 다시 구성해
    // 보내야 하는 부담이 있었다 — 그 부담 자체가 토글 실패의 원인이 될 수 있어 가볍게 뺐다.
    @Getter
    @Setter
    @NoArgsConstructor
    public static class VisibilityRequest {

        // SaveRequest.isPublic과 같은 이유(Lombok 기본 setPublic이 JSON 프로퍼티명을 public으로
        // 깎는 문제) — setter를 직접 선언해 고정한다.
        @JsonProperty("isPublic")
        @Setter(AccessLevel.NONE)
        private boolean isPublic;

        public void setIsPublic(boolean isPublic) {
            this.isPublic = isPublic;
        }
    }

    // PATCH /api/v1/courses/{courseId}/name 전용 — 이름만 바꾼다. VisibilityRequest와 같은
    // 이유 — SaveRequest(PUT)는 stops 전체를 다시 요구해서 이름만 고치는 가벼운 인터랙션에도
    // 부담이 있었다.
    @Getter
    @Setter
    @NoArgsConstructor
    public static class NameRequest {

        @NotBlank(message = "코스 이름을 입력해주세요.")
        private String name;
    }

}
