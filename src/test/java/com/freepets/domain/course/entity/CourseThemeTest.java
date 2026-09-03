package com.freepets.domain.course.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;

class CourseThemeTest {

    @Test
    void 대분류가_달라도_소분류가_맞으면_매칭된다() {
        // 실제로 겪은 버그 — ACTIVITY의 categories를 LEISURE로만 좁혀뒀는데, 관광공사 분류체계상
        // EX(체험관광) 코드들은 우리 카테고리 기준 TOUR로 잡혀 있어서 이미 smallCategoryCodes에
        // 넣어둔 시설 상당수가 대분류 게이트에서 먼저 걸려 제외됐다. matchesFacilityDetail은
        // 이제 categories와 무관하게 소분류만으로 판정해야 한다.
        Facility facility = facility(FacilityCategory.CULTURE, "NA020900"); // SEASIDE_WALK엔 CULTURE가 없음

        assertThat(CourseTheme.SEASIDE_WALK.matchesFacilityDetail(facility)).isTrue();
    }

    @Test
    void 소분류_코드가_없으면_매칭되지_않는다() {
        Facility facility = facility(FacilityCategory.TOUR, null);

        assertThat(CourseTheme.SEASIDE_WALK.matchesFacilityDetail(facility)).isFalse();
    }

    @Test
    void 소분류_코드가_이_테마_목록에_없으면_매칭되지_않는다() {
        Facility facility = facility(FacilityCategory.TOUR, "HS010100"); // 고궁 — SIGHTSEEING 소속

        assertThat(CourseTheme.SEASIDE_WALK.matchesFacilityDetail(facility)).isFalse();
    }

    @Test
    void ACTIVITY는_캠핑_소분류를_포함한다() {
        Facility campsite = facility(FacilityCategory.LEISURE, "AC050200"); // 오토캠핑장

        assertThat(CourseTheme.ACTIVITY.matchesFacilityDetail(campsite)).isTrue();
    }

    @Test
    void ACTIVITY는_체험관광_코드가_TOUR_카테고리여도_매칭된다() {
        // EX03(체험마을 등)은 우리 카테고리 기준 TOUR다 — categories에 TOUR를 넣지 않았다면
        // (혹은 categories로 게이트했다면) 이 테스트가 실패해야 한다.
        Facility experienceVillage = facility(FacilityCategory.TOUR, "EX030100"); // 체험마을

        assertThat(CourseTheme.ACTIVITY.matchesFacilityDetail(experienceVillage)).isTrue();
    }

    private Facility facility(
            FacilityCategory category,
            String smallCategoryCode
    ) {
        Facility facility = Facility.builder()
                .name("테스트시설")
                .category(category)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("128.0"))
                .smallCategoryCode(smallCategoryCode)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", 1L);
        return facility;
    }

}
