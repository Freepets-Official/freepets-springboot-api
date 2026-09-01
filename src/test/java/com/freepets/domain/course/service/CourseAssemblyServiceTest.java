package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;

class CourseAssemblyServiceTest {

    private final CourseAssemblyService courseAssemblyService = new CourseAssemblyService();

    @Test
    void 같은_카테고리는_점수가_가장_높은_1곳만_채택한다() {
        // 카페 두 곳(점수 90, 80)과 관광지 한 곳(점수 85) — 점수 desc로 이미 정렬해 넘긴다.
        Facility cafeHigh = facility(1L, "카페A", FacilityCategory.CAFE, "37.0", "128.0");
        Facility tour = facility(2L, "관광지A", FacilityCategory.TOUR, "37.001", "128.001");
        Facility cafeLow = facility(3L, "카페B", FacilityCategory.CAFE, "37.002", "128.002");

        List<Facility> result = courseAssemblyService.assemble(List.of(cafeHigh, tour, cafeLow));

        assertThat(result).extracting(Facility::getFacilityId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 스톱_상한을_넘는_후보는_잘린다() {
        List<Facility> candidates = List.of(
                facility(1L, "A", FacilityCategory.CAFE, "37.0", "128.0"),
                facility(2L, "B", FacilityCategory.TOUR, "37.01", "128.01"),
                facility(3L, "C", FacilityCategory.RESTAURANT, "37.02", "128.02"),
                facility(4L, "D", FacilityCategory.STAY, "37.03", "128.03"),
                facility(5L, "E", FacilityCategory.SHOPPING, "37.04", "128.04")
        );

        List<Facility> result = courseAssemblyService.assemble(candidates);

        assertThat(result).hasSize(CourseAssemblyService.MAX_RECOMMENDED_STOPS);
    }

    @Test
    void 좌표_없는_시설은_조립_대상에서_제외된다() {
        Facility noCoordinate = Facility.builder()
                .name("좌표없음")
                .category(FacilityCategory.CAFE)
                .build();
        ReflectionTestUtils.setField(noCoordinate, "facilityId", 9L);

        List<Facility> result = courseAssemblyService.assemble(List.of(noCoordinate));

        assertThat(result).isEmpty();
    }

    @Test
    void 최근접_이웃_방식으로_동선이_왔다갔다하지_않게_재정렬한다() {
        // 1(0,0) 점수1위 시작 → 가장 가까운 3(0,0.001) → 그 다음 2(0,10)
        Facility first = facility(1L, "A", FacilityCategory.CAFE, "0", "0");
        Facility far = facility(2L, "B", FacilityCategory.TOUR, "0", "10");
        Facility near = facility(3L, "C", FacilityCategory.RESTAURANT, "0", "0.001");

        List<Facility> result = courseAssemblyService.assemble(List.of(first, far, near));

        assertThat(result).extracting(Facility::getFacilityId).containsExactly(1L, 3L, 2L);
    }

    private Facility facility(
            Long facilityId,
            String name,
            FacilityCategory category,
            String lat,
            String lng
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(category)
                .lat(new BigDecimal(lat))
                .lng(new BigDecimal(lng))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
