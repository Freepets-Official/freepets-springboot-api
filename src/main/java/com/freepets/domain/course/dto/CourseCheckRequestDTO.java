package com.freepets.domain.course.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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

        // 10 = CourseAssemblyService.MAX_CUSTOM_STOPS와 맞춘다 — 스톱이 많을수록 이 요청 하나가
        // 스톱 수만큼 PetCheckJudgeService.judgeGroup을 호출해 무거워진다.
        @NotEmpty(message = "facilityIds는 비어있을 수 없습니다.")
        @Size(max = 10, message = "코스는 최대 10곳까지만 담을 수 있습니다.")
        private List<Long> facilityIds;
    }

}
