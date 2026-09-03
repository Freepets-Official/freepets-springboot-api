package com.freepets.domain.course.converter;

import java.util.Comparator;
import java.util.List;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseStop;

public class CourseConverter {

    private CourseConverter() {}

    public static CourseResponseDTO.MyCourse toMyCourse(Course course) {
        return new CourseResponseDTO.MyCourse(
                course.getCourseId(),
                course.getName(),
                course.getDescription(),
                stopIdsOf(course),
                course.getCreatedAt(),
                course.isPublic()
        );
    }

    public static CourseResponseDTO.PublicCourse toPublicCourse(Course course) {
        return new CourseResponseDTO.PublicCourse(
                course.getCourseId(),
                course.getName(),
                course.getDescription(),
                course.getUser().getNickname(),
                stopIdsOf(course),
                course.getCreatedAt()
        );
    }

    private static List<Long> stopIdsOf(Course course) {
        return course.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStop::getStopOrder))
                .map(stop -> stop.getFacility().getFacilityId())
                .toList();
    }

}
