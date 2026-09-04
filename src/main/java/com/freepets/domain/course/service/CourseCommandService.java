package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.converter.CourseConverter;
import com.freepets.domain.course.dto.CourseRequestDTO;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.entity.CourseStop;
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
    private final CourseAssemblyService courseAssemblyService;

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
                .isPublic(request.isPublic())
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
        course.updateVisibility(request.isPublic());

        return CourseConverter.toMyCourse(course);
    }

    /**
     * POST /api/v1/courses/optimize-order — 저장하지 않고 스톱 순서만 최근접 이웃 방식으로
     * 다듬어 미리 보여준다. AI 코스를 fork했거나 직접 검색해서 스톱을 추가/삭제한 뒤, 동선을
     * 정리하고 싶을 때 쓴다(그대로 저장하려면 이 결과를 다시 POST/PUT에 넣어야 한다).
     */
    public CourseResponseDTO.OrderResult optimizeOrder(List<Long> stopIds) {
        List<Facility> stops = findFacilitiesInOrder(stopIds);
        List<Facility> reordered = courseAssemblyService.reorderForCustomEdit(stops);

        List<Long> reorderedIds = reordered.stream().map(Facility::getFacilityId).toList();
        return new CourseResponseDTO.OrderResult(reorderedIds);
    }

    /**
     * PUT /api/v1/courses/{courseId}/stops/{stopOrder} — 그 자리(0부터 시작하는 순서)의
     * 스톱만 다른 시설로 교체한다. 나머지 스톱과 순서는 그대로 — "1·2·3·4·5에서 4번만
     * 6번으로" 같은 한 곳 스왑을 위해 매번 stopIds 전체를 다시 구성해 보낼 필요가 없게 한다.
     * 스톱을 추가하거나 빼서 개수 자체가 바뀌는 편집은 여전히 updateCourse(전체 교체)를 쓴다.
     */
    public CourseResponseDTO.MyCourse replaceStop(
            Long userId,
            Long courseId,
            int stopOrder,
            Long newFacilityId
    ) {
        Course course = findOwnedCourse(userId, courseId);
        List<Facility> facilitiesInOrder = course.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStop::getStopOrder))
                .map(CourseStop::getFacility)
                .collect(Collectors.toCollection(ArrayList::new));

        if (stopOrder < 0 || stopOrder >= facilitiesInOrder.size()) {
            throw new GeneralException(ErrorStatus.COURSE4043);
        }

        Facility newFacility = facilityRepository.findById(newFacilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        facilitiesInOrder.set(stopOrder, newFacility);
        course.replaceStops(facilitiesInOrder);

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
