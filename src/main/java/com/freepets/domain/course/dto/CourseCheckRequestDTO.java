package com.freepets.domain.course.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class CourseCheckRequestDTO {

    private CourseCheckRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotEmpty(message = "판별할 반려동물을 1마리 이상 선택해주세요.")
        private List<Long> petIds;

        @NotEmpty(message = "facilityIds는 비어있을 수 없습니다.")
        private List<Long> facilityIds;
    }

}
