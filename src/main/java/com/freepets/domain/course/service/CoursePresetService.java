package com.freepets.domain.course.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.entity.CourseStop;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.GeoUtils;

import lombok.RequiredArgsConstructor;

/**
 * GET /api/v1/courses/preset — 지역×테마 조합을 서버가 계산해 {@code courses(source=PRESET)}에
 * 캐시한다. 캐시 히트/미스와 무관하게 {@code score}/{@code distanceM}은 매 조회마다 새로 계산한다
 * — 캐시가 아끼는 건 "후보 스캔 + 정렬 + 조립"(무거운 부분)이고, 이미 정해진 스톱 목록에 대한
 * 점수·거리 재계산은 가볍다. 리뷰가 계속 쌓이는 걸 감안하면 오히려 매번 최신값을 보여주는 쪽이
 * 낫다. 나이틀리 재계산 트리거(스케줄러)는 이번 범위에 포함하지 않는다 — 최초 조회 시 캐시가
 * 없으면 그 자리에서 계산해 채워두는 지연 생성만 우선 구현한다(FacilityConditionLlmParser가
 * NOT_PROCESSED를 처음 만났을 때 채우는 것과 같은 패턴).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CoursePresetService {

    private static final int MINIMUM_CANDIDATE_COUNT = 2;

    private final CourseRepository courseRepository;
    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;
    private final CourseAssemblyService courseAssemblyService;

    public CourseResponseDTO.PresetCourseResult getPreset(
            String sido,
            String sigungu,
            CourseTheme theme
    ) {
        String area = buildAreaKey(sido, sigungu);

        Course course = courseRepository.findBySourceAndAreaAndTheme(CourseSource.PRESET, area, theme)
                .orElseGet(() -> courseRepository.save(compute(sido, sigungu, theme, area)));

        return toResult(course);
    }

    private Course compute(
            String sido,
            String sigungu,
            CourseTheme theme,
            String area
    ) {
        List<Facility> candidates = facilityRepository.findPresetCandidates(sido, sigungu, theme.getCategories());
        if (candidates.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4001);
        }

        Map<Long, Double> scoreById = scoreById(candidates);
        List<Facility> candidatesScoreDescSorted = candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (Facility facility) -> scoreById.getOrDefault(facility.getFacilityId(), 0.0)
                ).reversed())
                .toList();

        List<Facility> stops = courseAssemblyService.assembleWithoutCategoryDiversity(
                candidatesScoreDescSorted, CourseAssemblyService.MAX_RECOMMENDED_STOPS
        );
        if (stops.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4001);
        }

        String title = "%s %s 코스".formatted(sigungu != null ? sigungu : sido, theme.getLabel());
        Course course = Course.builder()
                .name(title)
                .source(CourseSource.PRESET)
                .area(area)
                .theme(theme)
                .build();
        course.replaceStops(stops);

        return course;
    }

    private CourseResponseDTO.PresetCourseResult toResult(Course course) {
        List<Facility> facilitiesInOrder = course.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStop::getStopOrder))
                .map(CourseStop::getFacility)
                .toList();

        Map<Long, Double> scoreById = scoreById(facilitiesInOrder);
        Facility origin = facilitiesInOrder.get(0);

        List<CourseResponseDTO.PresetStop> stopDtos = facilitiesInOrder.stream()
                .map(facility -> new CourseResponseDTO.PresetStop(
                        facility.getFacilityId(),
                        facility.getName(),
                        facility.getCategory(),
                        Math.round(scoreById.getOrDefault(facility.getFacilityId(), 0.0) * 10) / 10.0,
                        Math.round(GeoUtils.distanceMeters(
                                origin.getLat(), origin.getLng(), facility.getLat(), facility.getLng()
                        ))
                ))
                .toList();

        return new CourseResponseDTO.PresetCourseResult(course.getCourseId(), course.getName(), stopDtos);
    }

    private Map<Long, Double> scoreById(List<Facility> facilities) {
        List<Long> facilityIds = facilities.stream().map(Facility::getFacilityId).toList();
        return reviewRepository.aggregateByFacilityIdIn(facilityIds, ReviewReportStatus.ACCEPTED).stream()
                .collect(Collectors.toMap(FacilityReviewAggregate::facilityId, FacilityReviewAggregate::averageScore));
    }

    private String buildAreaKey(
            String sido,
            String sigungu
    ) {
        return sigungu != null ? sido + " " + sigungu : sido;
    }

}
