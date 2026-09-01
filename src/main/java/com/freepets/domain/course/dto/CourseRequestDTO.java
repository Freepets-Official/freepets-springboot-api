package com.freepets.domain.course.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
    }

}
