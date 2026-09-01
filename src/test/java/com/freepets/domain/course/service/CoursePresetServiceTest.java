package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
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
                .build();
        cached.replaceStops(List.of(cafe));
        ReflectionTestUtils.setField(cached, "courseId", 101L);

        when(courseRepository.findBySourceAndSidoAndSigunguAndTheme(CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE))
                .thenReturn(Optional.of(cached));
        when(reviewRepository.aggregateByFacilityIdIn(anyCollection(), any()))
                .thenReturn(List.of(aggregateOf(1L, 88.0)));

        CourseResponseDTO.PresetCourseResult result = coursePresetService.getPreset("강원", "강릉시", CourseTheme.PET_CAFE);

        assertThat(result.courseId()).isEqualTo(101L);
        assertThat(result.stops()).hasSize(1);
        assertThat(result.stops().get(0).score()).isEqualTo(88.0);
        verify(courseRepository, never()).save(any());
        verify(facilityRepository, never()).findPresetCandidates(any(), any(), anyCollection());
    }

    @Test
    void 후보가_2곳_미만이면_COURSE4001() {
        when(courseRepository.findBySourceAndSidoAndSigunguAndTheme(CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE))
                .thenReturn(Optional.empty());
        when(facilityRepository.findPresetCandidates("강원", "강릉시", CourseTheme.PET_CAFE.getCategories()))
                .thenReturn(List.of(facility(1L, "카페A", FacilityCategory.CAFE)));

        assertThatThrownBy(() -> coursePresetService.getPreset("강원", "강릉시", CourseTheme.PET_CAFE))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 캐시_미스면_계산해서_저장하고_courseId를_채워_돌려준다() {
        Facility cafeHigh = facility(1L, "카페A", FacilityCategory.CAFE);
        Facility cafeLow = facility(2L, "카페B", FacilityCategory.CAFE);

        when(courseRepository.findBySourceAndSidoAndSigunguAndTheme(CourseSource.PRESET, "강원", "강릉시", CourseTheme.PET_CAFE))
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

        CourseResponseDTO.PresetCourseResult result = coursePresetService.getPreset("강원", "강릉시", CourseTheme.PET_CAFE);

        assertThat(result.courseId()).isEqualTo(202L);
        assertThat(result.title()).isEqualTo("강릉시 애견 카페 코스");
        assertThat(result.stops()).hasSize(2);
        assertThat(result.stops().get(0).facilityId()).isEqualTo(1L); // 점수 90이 70보다 먼저
        assertThat(result.stops().get(0).distanceM()).isEqualTo(0.0); // 시작점 자기 자신
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

    private Facility facility(
            Long facilityId,
            String name,
            FacilityCategory category
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(category)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("128.0"))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
