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
                stopsOf(course),
                course.getCreatedAt(),
                course.isPublic()
        );
    }

    public static CourseResponseDTO.ShareResult toShareResult(Course course) {
        return new CourseResponseDTO.ShareResult(
                course.getCourseId(),
                course.getShareCode()
        );
    }

    public static CourseResponseDTO.PublicCourse toPublicCourse(Course course) {
        return new CourseResponseDTO.PublicCourse(
                course.getCourseId(),
                course.getName(),
                course.getDescription(),
                course.getUser().getNickname(),
                stopsOf(course),
                course.getCreatedAt()
        );
    }

    private static List<CourseResponseDTO.Stop> stopsOf(Course course) {
        return course.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStop::getStopOrder))
                .map(stop -> new CourseResponseDTO.Stop(
                        stop.getFacility().getFacilityId(),
                        stop.getVisitTime()
                ))
                .toList();
    }

}
