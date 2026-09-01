package com.freepets.domain.course.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.entity.CourseTheme;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // GET /api/v1/courses(내 코스) — MVP는 페이지네이션 없이 flat list(07-courses.md "결정된 사항" 참고).
    List<Course> findAllByUser_Id(Long userId);

    // PRESET 캐시 조회/재계산 대상 판단 — 지역×테마 조합당 최대 1행.
    Optional<Course> findBySourceAndAreaAndTheme(
            CourseSource source,
            String area,
            CourseTheme theme
    );

}
