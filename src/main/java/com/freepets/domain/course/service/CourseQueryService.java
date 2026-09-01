package com.freepets.domain.course.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.converter.CourseConverter;
import com.freepets.domain.course.dto.CourseResponseDTO;
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

}
