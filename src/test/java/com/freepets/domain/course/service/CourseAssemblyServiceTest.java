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

    @Test
    void 직접_편집_재정렬은_카테고리_상한_없이_모든_스톱을_최근접_이웃으로_재배치한다() {
        // 카페 3곳(같은 카테고리라 assemble()이었다면 1곳만 남았을 상황) — 재정렬은 다 유지한다.
        Facility first = facility(1L, "A", FacilityCategory.CAFE, "0", "0");
        Facility far = facility(2L, "B", FacilityCategory.CAFE, "0.02", "0");
        Facility near = facility(3L, "C", FacilityCategory.CAFE, "0.001", "0");

        List<Facility> result = courseAssemblyService.reorderForCustomEdit(List.of(first, far, near));

        assertThat(result).extracting(Facility::getFacilityId).containsExactly(1L, 3L, 2L);
    }

    @Test
    void 직접_편집_재정렬에서_좌표_없는_시설은_원래_상대_순서를_유지한_채_뒤에_붙는다() {
        Facility withCoordinate = facility(1L, "A", FacilityCategory.CAFE, "0", "0");
        Facility noCoordinate1 = Facility.builder().name("좌표없음1").category(FacilityCategory.CAFE).build();
        ReflectionTestUtils.setField(noCoordinate1, "facilityId", 8L);
        Facility noCoordinate2 = Facility.builder().name("좌표없음2").category(FacilityCategory.CAFE).build();
        ReflectionTestUtils.setField(noCoordinate2, "facilityId", 9L);

        List<Facility> result = courseAssemblyService.reorderForCustomEdit(
                List.of(noCoordinate1, withCoordinate, noCoordinate2)
        );

        assertThat(result).extracting(Facility::getFacilityId).containsExactly(1L, 8L, 9L);
    }

    @Test
    void 식사_후보가_있으면_마지막_자리를_식사_스톱으로_채운다() {
        Facility cafe = facility(1L, "카페A", FacilityCategory.CAFE, "0", "0");
        Facility tour = facility(2L, "관광지A", FacilityCategory.TOUR, "0.001", "0");
        Facility culture = facility(3L, "문화A", FacilityCategory.CULTURE, "0.002", "0");
        Facility meal = facility(4L, "식당A", FacilityCategory.RESTAURANT, "0.0015", "0");

        List<Facility> result = courseAssemblyService.assemble(
                List.of(cafe, tour, culture), List.of(meal), DEFAULT_DISTANCE
        );

        assertThat(result).hasSize(4);
        assertThat(result).extracting(Facility::getFacilityId).contains(4L);
    }

    @Test
    void 식사_후보가_전부_기준_거리_밖이면_테마_후보만으로_채운다() {
        // 신전해변류 버그와 같은 이유 — 억지로 먼 식당을 끼워넣어 자리를 낭비하지 않는다.
        Facility cafe = facility(1L, "카페A", FacilityCategory.CAFE, "0", "0");
        Facility tour = facility(2L, "관광지A", FacilityCategory.TOUR, "0.001", "0");
        Facility culture = facility(3L, "문화A", FacilityCategory.CULTURE, "0.002", "0");
        Facility stay = facility(5L, "숙소A", FacilityCategory.STAY, "0.003", "0");
        Facility farMeal = facility(4L, "식당A", FacilityCategory.RESTAURANT, "1.0", "0"); // 약 111km

        List<Facility> result = courseAssemblyService.assemble(
                List.of(cafe, tour, culture, stay), List.of(farMeal), DEFAULT_DISTANCE
        );

        assertThat(result).extracting(Facility::getFacilityId).containsExactlyInAnyOrder(1L, 2L, 3L, 5L);
    }

    @Test
    void 식사_후보가_비어있으면_기존_assemble과_동일하게_동작한다() {
        Facility cafeHigh = facility(1L, "카페A", FacilityCategory.CAFE, "37.0", "128.0");
        Facility tour = facility(2L, "관광지A", FacilityCategory.TOUR, "37.001", "128.001");

        List<Facility> result = courseAssemblyService.assemble(List.of(cafeHigh, tour), List.of(), DEFAULT_DISTANCE);

        assertThat(result).extracting(Facility::getFacilityId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 카테고리_다양성_미적용_조립도_식사_스톱을_마지막_자리에_채운다() {
        // preset의 "애견 카페" 코스처럼 후보가 전부 같은 카테고리인 경우에도 식사 스톱은 그대로 붙는다.
        Facility cafeA = facility(1L, "카페A", FacilityCategory.CAFE, "37.0", "128.0");
        Facility cafeB = facility(2L, "카페B", FacilityCategory.CAFE, "37.001", "128.001");
        Facility cafeC = facility(3L, "카페C", FacilityCategory.CAFE, "37.002", "128.002");
        Facility meal = facility(4L, "식당A", FacilityCategory.RESTAURANT, "37.0015", "128.0015");

        List<Facility> result = courseAssemblyService.assembleWithoutCategoryDiversity(
                List.of(cafeA, cafeB, cafeC), List.of(meal), 4, DEFAULT_DISTANCE
        );

        assertThat(result).hasSize(4);
        assertThat(result).extracting(Facility::getFacilityId).contains(4L);
    }

    @Test
    void appendMealStop은_이미_채택된_스톱과_같은_시설은_후보에서_제외한다() {
        Facility stop = facility(1L, "카페A", FacilityCategory.CAFE, "0", "0");
        Facility sameFacility = facility(1L, "카페A", FacilityCategory.CAFE, "0", "0"); // 같은 id, 다른 인스턴스

        List<Facility> result = courseAssemblyService.appendMealStop(List.of(stop), List.of(sameFacility), DEFAULT_DISTANCE);

        assertThat(result).hasSize(1);
    }

    @Test
    void appendMealStop은_스톱이_비어있으면_그대로_반환한다() {
        Facility meal = facility(1L, "식당A", FacilityCategory.RESTAURANT, "0", "0");

        List<Facility> result = courseAssemblyService.appendMealStop(List.of(), List.of(meal), DEFAULT_DISTANCE);

        assertThat(result).isEmpty();
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
