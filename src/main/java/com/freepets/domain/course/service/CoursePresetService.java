package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import com.freepets.domain.facility.repository.SidoSigungu;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.GeoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GET /api/v1/courses/preset — 지역×테마 조합을 서버가 계산해 {@code courses(source=PRESET)}에
 * 캐시한다. 캐시 히트/미스와 무관하게 {@code score}/{@code distanceM}은 매 조회마다 새로 계산한다
 * — 캐시가 아끼는 건 "후보 스캔 + 정렬 + 조립"(무거운 부분)이고, 이미 정해진 스톱 목록에 대한
 * 점수·거리 재계산은 가볍다. 리뷰가 계속 쌓이는 걸 감안하면 오히려 매번 최신값을 보여주는 쪽이
 * 낫다.
 *
 * <p>최초 조회 시 캐시가 없으면 그 자리에서 계산해 채워두는 지연 생성(lazy) + {@link
 * CoursePresetScheduler}의 나이틀리 재계산(이미 캐시된 조합의 스톱 구성을 새로 고침), 두 경로 모두
 * 이 서비스를 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CoursePresetService {

    private static final int MINIMUM_CANDIDATE_COUNT = 2;

    private final CourseRepository courseRepository;
    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;
    private final CourseAssemblyService courseAssemblyService;

    /**
     * GET /api/v1/courses/themes — 테마 선택 드롭다운용. {@link CourseTheme}은 DB가 아니라
     * 코드에 고정된 값이라 프론트가 값·라벨을 하드코딩하지 않게 서버가 그대로 내려준다.
     */
    public CourseResponseDTO.ThemeList getThemes() {
        List<CourseResponseDTO.ThemeOption> themes = Arrays.stream(CourseTheme.values())
                .map(theme -> new CourseResponseDTO.ThemeOption(theme, theme.getLabel()))
                .toList();

        return new CourseResponseDTO.ThemeList(themes);
    }

    /**
     * GET /api/v1/courses/regions — 지역 선택 드롭다운용. 자유텍스트 입력을 받으면 "강원"처럼
     * 실제 저장된 값("강원특별자치도")과 다른 표기를 보내 후보가 0건이 되는 문제가 있어서, 실제
     * 동반 가능 시설이 있는 (sido, sigungu) 조합만 골라서 준다.
     */
    public CourseResponseDTO.RegionList getRegions() {
        Map<String, List<String>> sigungusBySido = new LinkedHashMap<>();

        for (SidoSigungu region : facilityRepository.findDistinctRegions()) {
            List<String> sigungus = sigungusBySido.computeIfAbsent(region.getSido(), key -> new ArrayList<>());
            if (region.getSigungu() != null) {
                sigungus.add(region.getSigungu());
            }
        }

        List<CourseResponseDTO.SidoRegion> sidos = sigungusBySido.entrySet().stream()
                .map(entry -> new CourseResponseDTO.SidoRegion(entry.getKey(), entry.getValue()))
                .toList();

        return new CourseResponseDTO.RegionList(sidos);
    }

    public CourseResponseDTO.PresetCourseResult getPreset(
            String sido,
            String sigungu,
            CourseTheme theme
    ) {
        Course course = courseRepository.findBySourceAndSidoAndSigunguAndTheme(CourseSource.PRESET, sido, sigungu, theme)
                .orElseGet(() -> courseRepository.save(newCourse(sido, sigungu, theme)));

        return toResult(course);
    }

    /**
     * {@link CoursePresetScheduler}가 쓴다. 이미 캐시된 지역×테마 조합 전부를 훑어 스톱 구성을
     * 다시 계산한다 — 후보가 하한 밑으로 떨어진 조합은 캐시를 지우지 않고 직전 스톱 구성을 그대로
     * 남겨둔다(일시적으로 후보가 줄었다고 이미 보여주던 코스를 갑자기 비울 이유는 없다).
     */
    public void recalculateAll() {
        List<Course> presets = courseRepository.findAllBySource(CourseSource.PRESET);
        for (Course course : presets) {
            try {
                recalculate(course);
            } catch (GeneralException exception) {
                log.warn("프리셋 코스 재계산을 건너뜁니다 — sido={}, sigungu={}, theme={}, 사유={}",
                        course.getSido(), course.getSigungu(), course.getTheme(), exception.getMessage());
            }
        }
    }

    private void recalculate(Course course) {
        List<Facility> stops = computeStops(course.getSido(), course.getSigungu(), course.getTheme());
        course.update(titleOf(course.getSido(), course.getSigungu(), course.getTheme()), null, stops);
    }

    private Course newCourse(
            String sido,
            String sigungu,
            CourseTheme theme
    ) {
        List<Facility> stops = computeStops(sido, sigungu, theme);

        Course course = Course.builder()
                .name(titleOf(sido, sigungu, theme))
                .source(CourseSource.PRESET)
                .sido(sido)
                .sigungu(sigungu)
                .theme(theme)
                .build();
        course.replaceStops(stops);

        return course;
    }

    private List<Facility> computeStops(
            String sido,
            String sigungu,
            CourseTheme theme
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

        return stops;
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

    private String titleOf(
            String sido,
            String sigungu,
            CourseTheme theme
    ) {
        return "%s %s 코스".formatted(sigungu != null ? sigungu : sido, theme.getLabel());
    }

}
