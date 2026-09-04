package com.freepets.domain.course.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.converter.CourseConverter;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.repository.CourseRepository;

import lombok.RequiredArgsConstructor;

// GET /api/v1/courses — 내 코스(CUSTOM) 목록. MVP는 페이지네이션 없이 flat list
// (07-courses.md "결정된 사항" 참고).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private final CourseRepository courseRepository;

    public List<CourseResponseDTO.MyCourse> getMyCourses(Long userId) {
        return courseRepository.findAllByUser_Id(userId).stream()
                .map(CourseConverter::toMyCourse)
                .toList();
    }

    /**
     * GET /api/v1/courses/public — 다른 사용자가 공개한 CUSTOM 코스 둘러보기. 전체 사용자
     * 대상이라 내 코스와 달리 페이지네이션이 필요하다(위 getMyCourses 주석 참고).
     */
    public CourseResponseDTO.PublicCourseResult getPublicCourses(Pageable pageable) {
        Page<Course> page = courseRepository.findAllBySourceAndIsPublicTrueOrderByCreatedAtDesc(
                CourseSource.CUSTOM, pageable
        );
        List<CourseResponseDTO.PublicCourse> items = page.getContent().stream()
                .map(CourseConverter::toPublicCourse)
                .toList();

        return new CourseResponseDTO.PublicCourseResult(items, page.getTotalElements());
    }

}
