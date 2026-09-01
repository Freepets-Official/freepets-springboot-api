package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.converter.CourseConverter;
import com.freepets.domain.course.dto.CourseRequestDTO;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

// POST/PUT/DELETE /api/v1/courses — 내 코스(CUSTOM) 저장/수정/삭제.
@Service
@RequiredArgsConstructor
@Transactional
public class CourseCommandService {

    private final CourseRepository courseRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public CourseResponseDTO.MyCourse createCourse(
            Long userId,
            CourseRequestDTO.SaveRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<Facility> stops = findFacilitiesInOrder(request.getStopIds());

        Course course = Course.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .source(CourseSource.CUSTOM)
                .build();
        course.replaceStops(stops);

        Course saved = courseRepository.save(course);
        return CourseConverter.toMyCourse(saved);
    }

    public CourseResponseDTO.MyCourse updateCourse(
            Long userId,
            Long courseId,
            CourseRequestDTO.SaveRequest request
    ) {
        Course course = findOwnedCourse(userId, courseId);
        List<Facility> stops = findFacilitiesInOrder(request.getStopIds());

        course.update(request.getName(), request.getDescription(), stops);

        return CourseConverter.toMyCourse(course);
    }

    public CourseResponseDTO.DeleteResult deleteCourse(
            Long userId,
            Long courseId
    ) {
        Course course = findOwnedCourse(userId, courseId);
        courseRepository.delete(course);

        return new CourseResponseDTO.DeleteResult(courseId);
    }

    private Course findOwnedCourse(
            Long userId,
            Long courseId
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.COURSE4041));

        if (!course.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.COURSE4042);
        }

        return course;
    }

    private List<Facility> findFacilitiesInOrder(List<Long> stopIds) {
        Map<Long, Facility> byId = new LinkedHashMap<>();
        facilityRepository.findAllById(stopIds).forEach(facility -> byId.put(facility.getFacilityId(), facility));

        List<Facility> ordered = new ArrayList<>();
        for (Long facilityId : stopIds) {
            Facility facility = byId.get(facilityId);
            if (facility == null) {
                throw new GeneralException(ErrorStatus.FACILITY4001);
            }
            ordered.add(facility);
        }
        return ordered;
    }

}
