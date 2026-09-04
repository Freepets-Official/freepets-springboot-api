package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseDistanceOption;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.SidoSigungu;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CoursePresetServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Spy
    private CourseAssemblyService courseAssemblyService = new CourseAssemblyService();

    @InjectMocks
    private CoursePresetService coursePresetService;

    @Test
    void 캐시_히트면_새로_계산하지_않는다() {
        Facility cafe = facility(1L, "카페A", FacilityCategory.CAFE);
        Course cached = Course.builder()
                .name("강릉 애견 카페 코스")
                .source(CourseSource.PRESET)
                .sido("강원")
                .sigungu("강릉시")
                .theme(CourseTheme.PET_CAFE)
                .distanceOption(CourseDistanceOption.FIVE_KM)
                .build();
        cached.replaceStops(List.of(cafe));
        ReflectionTestUtils.setField(cached, "courseId", 101L);

        when(courseRepository.findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
                CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE, CourseDistanceOption.FIVE_KM
        ))
                .thenReturn(Optional.of(cached));
        when(reviewRepository.aggregateByFacilityIdIn(anyCollection(), any()))
                .thenReturn(List.of(aggregateOf(1L, 88.0)));

        CourseResponseDTO.PresetCourseResult result = coursePresetService.getPreset("강원", "강릉시", Set.of(CourseTheme.PET_CAFE), CourseDistanceOption.FIVE_KM);

        assertThat(result.courseId()).isEqualTo(101L);
        assertThat(result.stops()).hasSize(1);
        assertThat(result.stops().get(0).score()).isEqualTo(88.0);
        verify(courseRepository, never()).save(any());
        // 캐시 히트라 테마(PET_CAFE) 후보 재계산은 안 해야 한다 — 다만 표시 단계에서 식사 스톱
        // 후보(RESTAURANT)를 조회하는 건 sampleForDisplay의 정상 동작이라 별개로 허용한다.
        verify(facilityRepository, never()).findPresetCandidates("강원", "강릉시", CourseTheme.PET_CAFE.getCategories());
    }

    @Test
    void 후보가_2곳_미만이면_COURSE4001() {
        when(courseRepository.findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
                CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE, CourseDistanceOption.FIVE_KM
        ))
                .thenReturn(Optional.empty());
        when(facilityRepository.findPresetCandidates("강원", "강릉시", CourseTheme.PET_CAFE.getCategories()))
                .thenReturn(List.of(facility(1L, "카페A", FacilityCategory.CAFE)));

        assertThatThrownBy(() -> coursePresetService.getPreset("강원", "강릉시", Set.of(CourseTheme.PET_CAFE), CourseDistanceOption.FIVE_KM))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 대분류만_같고_소분류가_다르면_후보에서_제외된다() {
        // TOUR 하나가 SEASIDE_WALK·SIGHTSEEING 등 여러 테마에 걸쳐 있어, 대분류만 보면 산속
        // 고궁도 "바다 산책" 후보로 잘못 섞일 수 있었다 — 소분류(lclsSystm3)까지 확인해야 한다.
        Facility beach = facility(1L, "해변A", FacilityCategory.TOUR, "NA020900"); // 해변. 해수욕장
        Facility palace = facility(2L, "고궁A", FacilityCategory.TOUR, "HS010100"); // 고궁(SIGHTSEEING 소속)

        when(courseRepository.findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
                CourseSource.PRESET, "강원", "강릉시", CourseTheme.SEASIDE_WALK, CourseDistanceOption.FIVE_KM
        )).thenReturn(Optional.empty());
        when(facilityRepository.findPresetCandidates("강원", "강릉시", CourseTheme.SEASIDE_WALK.getCategories()))
                .thenReturn(List.of(beach, palace));

        // 후보가 결국 1곳(beach)뿐이라 최소 후보 수(2) 미달로 예외가 나야 한다 — palace가 섞여
        // 2곳이 됐다면(소분류 확인이 빠졌다는 뜻) 이 예외 대신 성공 응답이 돌아온다.
        assertThatThrownBy(() -> coursePresetService.getPreset("강원", "강릉시", Set.of(CourseTheme.SEASIDE_WALK), CourseDistanceOption.FIVE_KM))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 캐시_미스면_계산해서_저장하고_courseId를_채워_돌려준다() {
        Facility cafeHigh = facility(1L, "카페A", FacilityCategory.CAFE);
        Facility cafeLow = facility(2L, "카페B", FacilityCategory.CAFE);

        when(courseRepository.findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
                CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE, CourseDistanceOption.FIVE_KM
        ))
                .thenReturn(Optional.empty());
        when(facilityRepository.findPresetCandidates("강원", "강릉시", CourseTheme.PET_CAFE.getCategories()))
                .thenReturn(List.of(cafeLow, cafeHigh)); // 순서 뒤섞여 들어와도 점수 desc로 정렬돼야 함
        when(reviewRepository.aggregateByFacilityIdIn(anyCollection(), any()))
                .thenReturn(List.of(aggregateOf(1L, 90.0), aggregateOf(2L, 70.0)));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "courseId", 202L);
            return saved;
        });

        CourseResponseDTO.PresetCourseResult result = coursePresetService.getPreset("강원", "강릉시", Set.of(CourseTheme.PET_CAFE), CourseDistanceOption.FIVE_KM);

        assertThat(result.courseId()).isEqualTo(202L);
        assertThat(result.title()).isEqualTo("강릉시 애견 카페 코스");
        assertThat(result.stops()).hasSize(2);
        assertThat(result.stops().get(0).facilityId()).isEqualTo(1L); // 점수 90이 70보다 먼저
        assertThat(result.stops().get(0).distanceM()).isEqualTo(0.0); // 시작점 자기 자신
    }

    @Test
    void 근처_식당_후보가_있으면_마지막_자리를_식사_스톱으로_채운다() {
        Facility cafeHigh = facility(1L, "카페A", FacilityCategory.CAFE);
        Facility cafeLow = facility(2L, "카페B", FacilityCategory.CAFE);
        Facility meal = facility(3L, "식당A", FacilityCategory.RESTAURANT, null);

        when(courseRepository.findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
                CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE, CourseDistanceOption.FIVE_KM
        )).thenReturn(Optional.empty());
        when(facilityRepository.findPresetCandidates("강원", "강릉시", CourseTheme.PET_CAFE.getCategories()))
                .thenReturn(List.of(cafeLow, cafeHigh));
        when(facilityRepository.findPresetCandidates("강원", "강릉시", Set.of(FacilityCategory.RESTAURANT)))
                .thenReturn(List.of(meal));
        when(reviewRepository.aggregateByFacilityIdIn(anyCollection(), any()))
                .thenReturn(List.of(aggregateOf(1L, 90.0), aggregateOf(2L, 70.0)));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "courseId", 202L);
            return saved;
        });

        CourseResponseDTO.PresetCourseResult result = coursePresetService
                .getPreset("강원", "강릉시", Set.of(CourseTheme.PET_CAFE), CourseDistanceOption.FIVE_KM);

        assertThat(result.stops()).hasSize(3);
        CourseResponseDTO.PresetStop mealStop = result.stops().stream()
                .filter(CourseResponseDTO.PresetStop::isMealStop)
                .findFirst().orElseThrow();
        assertThat(mealStop.facilityId()).isEqualTo(3L);
    }

    @Test
    void 캐시된_풀이_표시_개수보다_크면_조회할_때마다_다른_구성이_나올_수_있다() {
        // 풀(8곳)을 캐시해두고, 조회마다 그중 4곳을 무작위로 뽑아 보여준다 — 같은 조합을
        // 반복 조회해도 매번 똑같은 4곳만 나오지 않는지 확인한다.
        List<Facility> pool = new ArrayList<>();
        for (long id = 1; id <= 8; id++) {
            pool.add(facility(id, "카페" + id, FacilityCategory.CAFE));
        }
        Course cached = cachedCourseWithStops(pool);

        when(courseRepository.findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
                CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE, CourseDistanceOption.FIVE_KM
        )).thenReturn(Optional.of(cached));
        when(reviewRepository.aggregateByFacilityIdIn(anyCollection(), any())).thenReturn(List.of());

        Set<List<Long>> distinctResults = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            CourseResponseDTO.PresetCourseResult result = coursePresetService
                    .getPreset("강원", "강릉시", Set.of(CourseTheme.PET_CAFE), CourseDistanceOption.FIVE_KM);
            assertThat(result.stops()).hasSize(4); // 풀은 8곳이어도 표시는 항상 4곳
            distinctResults.add(result.stops().stream().map(CourseResponseDTO.PresetStop::facilityId).toList());
        }

        assertThat(distinctResults).hasSizeGreaterThan(1); // 30번 다 똑같지는 않다
    }

    @Test
    void 무작위로_뽑아도_카테고리_상한을_지킨다() {
        // 풀 6곳 카페 + 2곳 관광지 — 표시 4곳 기준 상한(절반=2)이 무작위 추첨 후에도 지켜져야 한다.
        List<Facility> pool = new ArrayList<>();
        for (long id = 1; id <= 6; id++) {
            pool.add(facility(id, "카페" + id, FacilityCategory.CAFE));
        }
        pool.add(facility(7L, "관광지A", FacilityCategory.TOUR));
        pool.add(facility(8L, "관광지B", FacilityCategory.TOUR));
        Course cached = cachedCourseWithStops(pool);

        when(courseRepository.findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(
                CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE, CourseDistanceOption.FIVE_KM
        )).thenReturn(Optional.of(cached));
        when(reviewRepository.aggregateByFacilityIdIn(anyCollection(), any())).thenReturn(List.of());

        for (int i = 0; i < 30; i++) {
            CourseResponseDTO.PresetCourseResult result = coursePresetService
                    .getPreset("강원", "강릉시", Set.of(CourseTheme.PET_CAFE), CourseDistanceOption.FIVE_KM);
            long cafeCount = result.stops().stream().filter(stop -> stop.category() == FacilityCategory.CAFE).count();
            assertThat(result.stops()).hasSize(4);
            assertThat(cafeCount).isLessThanOrEqualTo(2);
        }
    }

    private Course cachedCourseWithStops(List<Facility> stops) {
        Course course = Course.builder()
                .name("테스트 코스")
                .source(CourseSource.PRESET)
                .sido("강원")
                .sigungu("강릉시")
                .theme(CourseTheme.PET_CAFE)
                .distanceOption(CourseDistanceOption.FIVE_KM)
                .build();
        course.replaceStops(stops);
        return course;
    }

    @Test
    void 테마를_여러_개_고르면_캐시하지_않고_즉시_계산한다() {
        Facility cafe = facility(1L, "카페A", FacilityCategory.CAFE);
        Facility tour = facility(2L, "해변A", FacilityCategory.TOUR, "NA020900"); // 해변. 해수욕장 — SEASIDE_WALK 매칭
        Set<FacilityCategory> unionCategories = Set.of(FacilityCategory.CAFE, FacilityCategory.TOUR, FacilityCategory.LEISURE);

        when(facilityRepository.findPresetCandidates("강원", "강릉시", unionCategories))
                .thenReturn(List.of(cafe, tour));
        when(reviewRepository.aggregateByFacilityIdIn(anyCollection(), any()))
                .thenReturn(List.of(aggregateOf(1L, 90.0), aggregateOf(2L, 80.0)));

        CourseResponseDTO.PresetCourseResult result = coursePresetService.getPreset(
                "강원", "강릉시", Set.of(CourseTheme.PET_CAFE, CourseTheme.SEASIDE_WALK), null
        );

        assertThat(result.courseId()).isNull(); // 다중 테마 결과는 courses 테이블에 저장되지 않는다
        assertThat(result.title()).isEqualTo("강릉시 바다 산책 · 애견 카페 코스"); // enum 선언 순서로 고정
        assertThat(result.stops()).hasSize(2);
        verify(courseRepository, never()).save(any());
        verify(courseRepository, never())
                .findBySourceAndSidoAndSigunguAndThemeAndDistanceOption(any(), any(), any(), any(), any());
    }

    @Test
    void 테마_목록은_enum_전체를_라벨과_함께_반환한다() {
        CourseResponseDTO.ThemeList result = coursePresetService.getThemes();

        assertThat(result.themes()).hasSize(CourseTheme.values().length);
        assertThat(result.themes()).extracting(CourseResponseDTO.ThemeOption::value)
                .contains(CourseTheme.PET_CAFE);
        assertThat(result.themes()).extracting(CourseResponseDTO.ThemeOption::label)
                .contains("애견 카페");
    }

    @Test
    void 시설이_스톱으로_포함된_프리셋_캐시를_전부_무효화한다() {
        Course cached1 = Course.builder()
                .name("강릉 애견 카페 코스").source(CourseSource.PRESET)
                .sido("강원특별자치도").sigungu("강릉시").theme(CourseTheme.PET_CAFE)
                .distanceOption(CourseDistanceOption.FIVE_KM)
                .build();
        Course cached2 = Course.builder()
                .name("속초 힐링 코스").source(CourseSource.PRESET)
                .sido("강원특별자치도").sigungu("속초시").theme(CourseTheme.HEALING)
                .distanceOption(CourseDistanceOption.FIVE_KM)
                .build();
        when(courseRepository.findAllBySourceAndStops_Facility_FacilityId(CourseSource.PRESET, 1L))
                .thenReturn(List.of(cached1, cached2));

        coursePresetService.invalidateCoursesContaining(1L);

        verify(courseRepository).deleteAll(List.of(cached1, cached2));
    }

    @Test
    void 걸리는_캐시가_없으면_아무것도_지우지_않는다() {
        when(courseRepository.findAllBySourceAndStops_Facility_FacilityId(CourseSource.PRESET, 999L))
                .thenReturn(List.of());

        coursePresetService.invalidateCoursesContaining(999L);

        verify(courseRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void 거리_선택지_목록은_enum_전체를_라벨_미터와_함께_반환한다() {
        CourseResponseDTO.DistanceOptionList result = coursePresetService.getDistanceOptions();

        assertThat(result.options()).hasSize(CourseDistanceOption.values().length);
        assertThat(result.options()).extracting(CourseResponseDTO.DistanceOption::value)
                .contains(CourseDistanceOption.FIVE_KM);
        assertThat(result.options()).extracting(CourseResponseDTO.DistanceOption::label)
                .contains("5km");
        assertThat(result.options()).extracting(CourseResponseDTO.DistanceOption::meters)
                .contains(5000);
    }

    @Test
    void 지역_목록은_시도별로_시군구를_묶어서_반환하고_시군구_없는_경우도_다룬다() {
        when(facilityRepository.findDistinctRegions()).thenReturn(List.of(
                sidoSigungu("강원특별자치도", "강릉시"),
                sidoSigungu("강원특별자치도", "속초시"),
                sidoSigungu("세종특별자치시", null) // 시/군/구 단위가 없는 광역시급 지역
        ));

        CourseResponseDTO.RegionList result = coursePresetService.getRegions();

        assertThat(result.sidos()).hasSize(2);
        assertThat(result.sidos().get(0).sido()).isEqualTo("강원특별자치도");
        assertThat(result.sidos().get(0).sigungus()).containsExactly("강릉시", "속초시");
        assertThat(result.sidos().get(1).sido()).isEqualTo("세종특별자치시");
        assertThat(result.sidos().get(1).sigungus()).isEmpty();
    }

    private SidoSigungu sidoSigungu(
            String sido,
            String sigungu
    ) {
        return new SidoSigungu() {
            @Override
            public String getSido() {
                return sido;
            }

            @Override
            public String getSigungu() {
                return sigungu;
            }
        };
    }

    @Test
    void 재계산은_캐시된_스톱_구성을_새로_계산해_갱신한다() {
        Facility cafeOld = facility(1L, "카페구", FacilityCategory.CAFE);
        Facility cafeNew = facility(2L, "카페신", FacilityCategory.CAFE);
        Course cached = Course.builder()
                .name("강릉 애견 카페 코스")
                .source(CourseSource.PRESET)
                .sido("강원")
                .sigungu("강릉시")
                .theme(CourseTheme.PET_CAFE)
                .distanceOption(CourseDistanceOption.FIVE_KM)
                .build();
        cached.replaceStops(List.of(cafeOld));

        when(courseRepository.findAllBySource(CourseSource.PRESET)).thenReturn(List.of(cached));
        when(facilityRepository.findPresetCandidates("강원", "강릉시", CourseTheme.PET_CAFE.getCategories()))
                .thenReturn(List.of(cafeNew));

        coursePresetService.recalculateAll();

        // 새 후보가 1곳뿐이라 최소 후보 수(2)를 못 채워 예외가 나지만, recalculateAll이 잡아서
        // 배치 전체가 죽지 않고 기존 캐시(cafeOld)를 그대로 남겨두는지 확인.
        assertThat(cached.getStops()).extracting(stop -> stop.getFacility().getFacilityId())
                .containsExactly(1L);
    }

    private FacilityReviewAggregate aggregateOf(
            Long facilityId,
            double averageScore
    ) {
        return new FacilityReviewAggregate(facilityId, 5, averageScore, 4.0, 4.0, 4.0);
    }

    // CourseTheme.matchesFacilityDetail이 category뿐 아니라 smallCategoryCode도 확인하므로,
    // 이 파일 대부분을 차지하는 CAFE(PET_CAFE 테마 테스트용) 시설은 기본으로 매칭되는 소분류
    // 코드(FD050100 카페)를 넣어준다 — CAFE가 아니거나 다른 테마로 매칭시킬 시설은 아래 4개짜리
    // 오버로드로 직접 코드를 지정한다.
    private Facility facility(
            Long facilityId,
            String name,
            FacilityCategory category
    ) {
        return facility(facilityId, name, category, category == FacilityCategory.CAFE ? "FD050100" : null);
    }

    private Facility facility(
            Long facilityId,
            String name,
            FacilityCategory category,
            String smallCategoryCode
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(category)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("128.0"))
                .smallCategoryCode(smallCategoryCode)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
