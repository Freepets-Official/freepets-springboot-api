package com.freepets.domain.course.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.service.CourseLikedService;
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
