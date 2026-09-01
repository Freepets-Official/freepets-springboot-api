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
    private static final double DEFAULT_DISTANCE = CourseAssemblyService.DEFAULT_MAX_STOP_DISTANCE_METERS;

    @Test
    void 같은_카테고리는_점수가_가장_높은_1곳만_채택한다() {
        // 카페 두 곳(점수 90, 80)과 관광지 한 곳(점수 85) — 점수 desc로 이미 정렬해 넘긴다.
        Facility cafeHigh = facility(1L, "카페A", FacilityCategory.CAFE, "37.0", "128.0");
        Facility tour = facility(2L, "관광지A", FacilityCategory.TOUR, "37.001", "128.001");
        Facility cafeLow = facility(3L, "카페B", FacilityCategory.CAFE, "37.002", "128.002");

        List<Facility> result = courseAssemblyService.assemble(List.of(cafeHigh, tour, cafeLow), DEFAULT_DISTANCE);

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

        List<Facility> result = courseAssemblyService.assemble(candidates, DEFAULT_DISTANCE);

        assertThat(result).hasSize(CourseAssemblyService.MAX_RECOMMENDED_STOPS);
    }

    @Test
    void 좌표_없는_시설은_조립_대상에서_제외된다() {
        Facility noCoordinate = Facility.builder()
                .name("좌표없음")
                .category(FacilityCategory.CAFE)
                .build();
        ReflectionTestUtils.setField(noCoordinate, "facilityId", 9L);

        List<Facility> result = courseAssemblyService.assemble(List.of(noCoordinate), DEFAULT_DISTANCE);

        assertThat(result).isEmpty();
    }

    @Test
    void 최근접_이웃_방식으로_동선이_왔다갔다하지_않게_재정렬한다() {
        // 1(0,0) 점수1위 시작 → 가장 가까운 3(약 111m) → 그 다음 2(약 2.2km, 둘 다 5km 상한 이내)
        Facility first = facility(1L, "A", FacilityCategory.CAFE, "0", "0");
        Facility far = facility(2L, "B", FacilityCategory.TOUR, "0.02", "0");
        Facility near = facility(3L, "C", FacilityCategory.RESTAURANT, "0.001", "0");

        List<Facility> result = courseAssemblyService.assemble(List.of(first, far, near), DEFAULT_DISTANCE);

        assertThat(result).extracting(Facility::getFacilityId).containsExactly(1L, 3L, 2L);
    }

    @Test
    void 이미_채택된_스톱과_5km_넘게_떨어진_후보는_점수가_높아도_제외된다() {
        // near(약 1.1km)는 채택, far(약 111km, 위도 1도 차이)는 점수가 더 높아도(먼저 순회) 제외돼야 한다.
        Facility anchor = facility(1L, "앵커", FacilityCategory.CAFE, "0", "0");
        Facility far = facility(2L, "먼곳", FacilityCategory.TOUR, "1.0", "0");
        Facility near = facility(3L, "가까운곳", FacilityCategory.RESTAURANT, "0.01", "0");

        List<Facility> result = courseAssemblyService.assemble(List.of(anchor, far, near), DEFAULT_DISTANCE);

        assertThat(result).extracting(Facility::getFacilityId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void 카테고리_다양성_미적용_조립도_한_카테고리가_절반을_넘지_못한다() {
        // 카페 3곳 + 관광지 1곳, limit=4 → 카테고리 2종이라 상한 = ceil(4/2) = 2. 카페는 2곳까지만.
        Facility cafeA = facility(1L, "카페A", FacilityCategory.CAFE, "0", "0");
        Facility cafeB = facility(2L, "카페B", FacilityCategory.CAFE, "0.001", "0");
        Facility cafeC = facility(3L, "카페C", FacilityCategory.CAFE, "0.002", "0");
        Facility tour = facility(4L, "관광지A", FacilityCategory.TOUR, "0.003", "0");

        List<Facility> result = courseAssemblyService.assembleWithoutCategoryDiversity(
                List.of(cafeA, cafeB, cafeC, tour), 4, DEFAULT_DISTANCE
        );

        assertThat(result).extracting(Facility::getFacilityId).containsExactlyInAnyOrder(1L, 2L, 4L);
    }

    @Test
    void 카테고리_다양성_미적용_조립은_같은_카테고리도_전부_남긴다() {
        // preset의 "애견 카페" 테마처럼 후보가 전부 같은 카테고리(CAFE)인 경우.
        Facility cafeA = facility(1L, "카페A", FacilityCategory.CAFE, "37.0", "128.0");
        Facility cafeB = facility(2L, "카페B", FacilityCategory.CAFE, "37.001", "128.001");
        Facility cafeC = facility(3L, "카페C", FacilityCategory.CAFE, "37.002", "128.002");

        List<Facility> result = courseAssemblyService.assembleWithoutCategoryDiversity(
                List.of(cafeA, cafeB, cafeC), 4, DEFAULT_DISTANCE
        );

        assertThat(result).extracting(Facility::getFacilityId).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void 카테고리_다양성_미적용_조립도_limit은_지킨다() {
        Facility cafeA = facility(1L, "카페A", FacilityCategory.CAFE, "37.0", "128.0");
        Facility cafeB = facility(2L, "카페B", FacilityCategory.CAFE, "37.001", "128.001");

        List<Facility> result = courseAssemblyService.assembleWithoutCategoryDiversity(
                List.of(cafeA, cafeB), 1, DEFAULT_DISTANCE
        );

        assertThat(result).hasSize(1);
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
