package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseDistanceOption;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.entity.CourseStop;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
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
     * GET /api/v1/courses/distance-options — 거리 슬라이더 선택지. preset은 이 값도 캐시 키에
     * 들어가는 고정 구간이라(CourseDistanceOption 참고) DB가 아니라 코드에 고정된 값을 그대로
     * 내려준다 — themes와 같은 이유.
     */
    public CourseResponseDTO.DistanceOptionList getDistanceOptions() {
        List<CourseResponseDTO.DistanceOption> options = Arrays.stream(CourseDistanceOption.values())
                .map(option -> new CourseResponseDTO.DistanceOption(option, option.getLabel(), option.getMeters()))
                .toList();

        return new CourseResponseDTO.DistanceOptionList(options);
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

    /**
     * 테마를 하나만 고르면 기존과 같이 (지역×테마×거리) 캐시를 쓴다. 여러 개를 고르면 캐시하지
     * 않고 즉시 계산해서 돌려준다 — 조합 수가 2ⁿ으로 늘어나(테마 5종이면 최대 31가지) 캐시 키에
     * 넣으면 사실상 캐시가 무의미해지기 때문이다. 다중 테마 결과는 {@code courses} 테이블에
     * 저장되지 않으므로 응답의 {@code courseId}가 null이다.
     */
    public CourseResponseDTO.PresetCourseResult getPreset(
            String sido,
            String sigungu,
            Set<CourseTheme> themes,
            CourseDistanceOption maxDistanceM
    ) {
        CourseDistanceOption distanceOption = maxDistanceM != null ? maxDistanceM : CourseDistanceOption.FIVE_KM;

        if (themes.size() == 1) {
            CourseTheme theme = themes.iterator().next();
            Course course = courseRepository
                    .findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(CourseSource.PRESET, sido, sigungu, theme, distanceOption)
                    .orElseGet(() -> courseRepository.save(newCourse(sido, sigungu, theme, distanceOption)));

            return toResult(course);
        }

        Set<FacilityCategory> categories = categoriesOf(themes);
        List<Facility> stops = computeStops(sido, sigungu, categories, distanceOption);
        return new CourseResponseDTO.PresetCourseResult(null, titleOf(sido, sigungu, themes), toStopDtos(stops));
    }

    /**
     * {@link CoursePresetCacheInvalidationListener}가 쓴다 — 시설이 추천 후보 자격을 잃었을 때
     * (비활성화·동반불가 전환) 그 시설을 스톱으로 쓰던 PRESET 캐시를 지운다. 나이틀리
     * 재계산은 이 조합이 하한 밑으로 떨어지면 예외를 잡아 기존 캐시를 그대로 남겨두므로
     * (recalculateAll 참고), 그것만으로는 "폐업했거나 반려동물 동반이 막힌 시설을 계속
     * 추천하는" 캐시가 영영 안 고쳐질 수 있다 — 행 자체를 지워서 다음 조회가 지연 생성
     * 경로(getPreset의 orElseGet)를 다시 타고 후보가 실제로 부족하면 COURSE4001을 정직하게
     * 돌려주게 한다.
     */
    public void invalidateCoursesContaining(Long facilityId) {
        List<Course> affected = courseRepository.findAllBySourceAndStops_Facility_FacilityId(CourseSource.PRESET, facilityId);
        if (affected.isEmpty()) {
            return;
        }

        log.info("시설 {}이(가) 추천 후보 자격을 잃어 프리셋 캐시 {}건을 무효화합니다.", facilityId, affected.size());
        courseRepository.deleteAll(affected);
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
        // 기존에 캐시된 조합은 이미 distanceOption을 갖고 있다 — 이 값 자체는 재계산 대상이
        // 아니라 조합의 정체성(캐시 키)이므로 그대로 재사용한다. 캐시는 단일 테마 조합만 갖고
        // 있으므로(다중 테마는 애초에 저장되지 않는다) Set.of(theme)로 감싸도 안전하다.
        Set<FacilityCategory> categories = categoriesOf(Set.of(course.getTheme()));
        List<Facility> stops = computeStops(course.getSido(), course.getSigungu(), categories, course.getDistanceOption());
        course.update(titleOf(course.getSido(), course.getSigungu(), Set.of(course.getTheme())), null, stops);
    }

    private Course newCourse(
            String sido,
            String sigungu,
            CourseTheme theme,
            CourseDistanceOption distanceOption
    ) {
        List<Facility> stops = computeStops(sido, sigungu, theme.getCategories(), distanceOption);

        Course course = Course.builder()
                .name(titleOf(sido, sigungu, Set.of(theme)))
                .source(CourseSource.PRESET)
                .sido(sido)
                .sigungu(sigungu)
                .theme(theme)
                .distanceOption(distanceOption)
                .build();
        course.replaceStops(stops);

        return course;
    }

    private List<Facility> computeStops(
            String sido,
            String sigungu,
            Set<FacilityCategory> categories,
            CourseDistanceOption distanceOption
    ) {
        List<Facility> candidates = facilityRepository.findPresetCandidates(sido, sigungu, categories);
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
                candidatesScoreDescSorted,
                CourseAssemblyService.MAX_RECOMMENDED_STOPS,
                distanceOption.getMeters()
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

        return new CourseResponseDTO.PresetCourseResult(course.getCourseId(), course.getName(), toStopDtos(facilitiesInOrder));
    }

    private List<CourseResponseDTO.PresetStop> toStopDtos(List<Facility> facilitiesInOrder) {
        Map<Long, Double> scoreById = scoreById(facilitiesInOrder);
        Facility origin = facilitiesInOrder.get(0);

        return facilitiesInOrder.stream()
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
    }

    private Map<Long, Double> scoreById(List<Facility> facilities) {
        List<Long> facilityIds = facilities.stream().map(Facility::getFacilityId).toList();
        return reviewRepository.aggregateByFacilityIdIn(facilityIds, ReviewReportStatus.ACCEPTED).stream()
                .collect(Collectors.toMap(FacilityReviewAggregate::facilityId, FacilityReviewAggregate::averageScore));
    }

    private Set<FacilityCategory> categoriesOf(Set<CourseTheme> themes) {
        return themes.stream()
                .flatMap(theme -> theme.getCategories().stream())
                .collect(Collectors.toSet());
    }

    private String titleOf(
            String sido,
            String sigungu,
            Set<CourseTheme> themes
    ) {
        // Set은 순서를 보장하지 않아, 매번 같은 제목이 나오도록 enum 선언 순서로 고정한다.
        String themeLabel = themes.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(CourseTheme::getLabel)
                .collect(Collectors.joining(" · "));
        return "%s %s 코스".formatted(sigungu != null ? sigungu : sido, themeLabel);
    }

}
