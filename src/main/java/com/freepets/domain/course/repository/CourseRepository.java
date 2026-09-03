package com.freepets.domain.course.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseDistanceOption;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.entity.CourseTheme;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // GET /api/v1/courses(내 코스) — MVP는 페이지네이션 없이 flat list(07-courses.md "결정된 사항" 참고).
    List<Course> findAllByUser_Id(Long userId);

    // GET /api/v1/courses/public — 다른 사용자가 공개한 CUSTOM 코스 둘러보기. 전체 사용자를
    // 대상으로 하는 목록이라 내 코스와 달리 페이지네이션이 필요하다.
    Page<Course> findAllBySourceAndIsPublicTrueOrderByCreatedAtDesc(CourseSource source, Pageable pageable);

    // PRESET 캐시 조회 — 지역×테마×거리 조합당 최대 1행. sigungu는 시/도 전체 대상일 때 null이라
    // 파라미터로 null이 들어오면 그대로 "IS NULL" 비교가 되는 derived query 기본 동작을 쓴다.
    Optional<Course> findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
            CourseSource source,
            String sido,
            String sigungu,
            CourseTheme theme,
            CourseDistanceOption distanceOption
    );

    // CoursePresetScheduler 나이틀리 재계산 대상 — 지금까지 한 번이라도 조회돼 캐시된 조합 전부.
    List<Course> findAllBySource(CourseSource source);

}
