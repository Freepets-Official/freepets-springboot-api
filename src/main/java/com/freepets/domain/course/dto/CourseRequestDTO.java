package com.freepets.domain.course.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

        @NotEmpty(message = "코스에 시설을 1곳 이상 담아주세요.")
        private List<Long> stopIds;

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

    // POST /api/v1/courses/optimize-order 전용 — 저장은 안 하고 스톱 순서만 최근접 이웃 방식으로
    // 다듬어본다(직접 검색해서 추가한 스톱까지 포함해 동선을 정리하고 싶을 때).
    @Getter
    @Setter
    @NoArgsConstructor
    public static class OptimizeOrderRequest {

        @NotEmpty(message = "코스에 시설을 1곳 이상 담아주세요.")
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

}
