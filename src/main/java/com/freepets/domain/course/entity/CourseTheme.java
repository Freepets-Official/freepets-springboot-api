package com.freepets.domain.course.entity;

import java.util.Set;

import com.freepets.domain.facility.entity.FacilityCategory;

/**
 * 프리셋 코스(source=PRESET)의 테마. 지역(sido/sigungu) × 이 값 조합으로 배치 계산·캐시한다.
 *
 * <p>1차 제안 5종 — 실제 화면 기획(예: "강릉 바다 산책 1일 코스", "강릉 애견 카페 반나절 코스")에서
 * 확인된 두 개(SEASIDE_WALK, PET_CAFE)를 기준으로 나머지 세 개를 채웠다. 추후 화면 기획이 늘어나면
 * 값을 더 추가하면 된다 — 기존 캐시된 코스에는 영향 없음(값 추가는 하위 호환).
 *
 * <p>{@code categories}는 후보 시설을 고르는 필터다. PET_CAFE처럼 카테고리가 하나뿐인 테마는
 * liked/similar의 "카테고리당 1곳" 규칙을 적용하면 스톱이 1개로 줄어버리므로, preset은
 * {@link com.freepets.domain.course.service.CourseAssemblyService#assembleWithoutCategoryDiversity}
 * (다양성 규칙 미적용)로 조립한다.
 */
public enum CourseTheme {

    /** 해변·산책로 위주. */
    SEASIDE_WALK("바다 산책", Set.of(FacilityCategory.TOUR, FacilityCategory.LEISURE)),

    /** 반려동물 동반 카페 위주. */
    PET_CAFE("애견 카페", Set.of(FacilityCategory.CAFE)),

    /** 한적하고 여유로운 곳 위주. */
    HEALING("힐링", Set.of(FacilityCategory.TOUR, FacilityCategory.CULTURE)),

    /** 관광지(명소) 위주. */
    SIGHTSEEING("관광지 위주", Set.of(FacilityCategory.TOUR, FacilityCategory.CULTURE, FacilityCategory.FESTIVAL)),

    /** 레포츠·액티비티 시설 위주. */
    ACTIVITY("액티비티", Set.of(FacilityCategory.LEISURE));

    private final String label;
    private final Set<FacilityCategory> categories;

    CourseTheme(
            String label,
            Set<FacilityCategory> categories
    ) {
        this.label = label;
        this.categories = categories;
    }

    public String getLabel() {
        return label;
    }

    public Set<FacilityCategory> getCategories() {
        return categories;
    }

}
