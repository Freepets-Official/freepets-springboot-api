package com.freepets.domain.course.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.course.service.CourseLikedService;
import com.freepets.domain.course.service.CoursePresetService;
import com.freepets.domain.course.service.CourseSimilarService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
// petIds 같은 쿼리 파라미터 제약을 동작시키려면 클래스 단위 선언이 필요하다(FacilityController와
// 같은 이유) — 위반은 ConstraintViolationException으로 올라가 GlobalExceptionHandler가 400으로 바꾼다.
@Validated
public class CourseController {

    private final CourseLikedService courseLikedService;
    private final CourseSimilarService courseSimilarService;
    private final CoursePresetService coursePresetService;

    // 로그인 불필요 — 유일하게 인증 없이 쓸 수 있는 코스 모드(07-courses.md 참고).
    @GetMapping("/preset")
    public ApiResponse<CourseResponseDTO.PresetCourseResult> getPresetCourse(
            @RequestParam @NotEmpty(message = "지역을 선택해주세요.") String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam CourseTheme theme
    ) {
        return ApiResponse.onSuccess(
                coursePresetService.getPreset(sido, sigungu, theme)
        );
    }

    @GetMapping("/liked")
    public ApiResponse<CourseResponseDTO.LikedCourseResult> getLikedCourse(
            @AuthenticationPrincipal Long userId,
            @RequestParam @NotEmpty(message = "반려동물을 1마리 이상 선택해주세요.") List<Long> petIds
    ) {
        return ApiResponse.onSuccess(
                courseLikedService.getLikedCourse(userId, petIds)
        );
    }

    @GetMapping("/similar")
    public ApiResponse<CourseResponseDTO.SimilarCourseResult> getSimilarCourse(
            @AuthenticationPrincipal Long userId,
            @RequestParam @NotEmpty(message = "반려동물을 1마리 이상 선택해주세요.") List<Long> petIds
    ) {
        return ApiResponse.onSuccess(
                courseSimilarService.getSimilarCourse(userId, petIds)
        );
    }

}
