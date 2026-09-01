package com.freepets.domain.course.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.course.dto.CourseCheckRequestDTO;
import com.freepets.domain.course.dto.CourseCheckResponseDTO;
import com.freepets.domain.course.service.CourseCheckService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class CourseCheckController {

    private final CourseCheckService courseCheckService;

    @PostMapping("/course-check")
    public ApiResponse<CourseCheckResponseDTO.CourseCheckResult> checkCourse(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CourseCheckRequestDTO.CreateRequest request
    ) {
        return ApiResponse.onSuccess(
                courseCheckService.checkCourse(userId, request.getPetIds(), request.getFacilityIds())
        );
    }

}
