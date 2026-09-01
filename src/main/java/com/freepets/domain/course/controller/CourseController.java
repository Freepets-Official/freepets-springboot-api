package com.freepets.domain.course.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.course.dto.CourseRequestDTO;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.course.service.CourseCommandService;
import com.freepets.domain.course.service.CourseLikedService;
import com.freepets.domain.course.service.CoursePresetService;
import com.freepets.domain.course.service.CourseQueryService;
import com.freepets.domain.course.service.CourseSimilarService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
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
    private final CourseQueryService courseQueryService;
    private final CourseCommandService courseCommandService;

    @GetMapping
    public ApiResponse<List<CourseResponseDTO.MyCourse>> getMyCourses(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                courseQueryService.getMyCourses(userId)
        );
    }

    @PostMapping
    public ApiResponse<CourseResponseDTO.MyCourse> createCourse(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CourseRequestDTO.SaveRequest request
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.createCourse(userId, request)
        );
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponseDTO.MyCourse> updateCourse(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody CourseRequestDTO.SaveRequest request
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.updateCourse(userId, courseId, request)
        );
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<CourseResponseDTO.DeleteResult> deleteCourse(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long courseId
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.deleteCourse(userId, courseId)
        );
    }

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
