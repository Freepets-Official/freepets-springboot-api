package com.freepets.infra.tourapi;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.freepets.domain.facility.entity.FacilityCategory;

/**
 * 관광공사 {@code contentTypeId}를 우리 시설 분류로 옮긴다.
 *
 * <p>도메인 enum이 외부 코드 체계를 알지 않도록 매핑을 여기 둔다.
 */
@Component
public class FacilityCategoryMapper {

    private static final Map<String, FacilityCategory> BY_CONTENT_TYPE = Map.of(
            "12", FacilityCategory.TOUR,
            "14", FacilityCategory.CULTURE,
            "15", FacilityCategory.FESTIVAL,
            "28", FacilityCategory.LEISURE,
            "32", FacilityCategory.STAY,
            "38", FacilityCategory.SHOPPING
    );

    private static final String CONTENT_TYPE_FOOD = "39";

    /** 분류체계 중분류 {@code FD05}는 카페·찻집이다. 나머지 {@code FD*}는 음식점으로 본다. */
    private static final String MEDIUM_CATEGORY_CAFE = "FD05";

    private static final Map<FacilityCategory, Integer> TO_CONTENT_TYPE = Map.of(
            FacilityCategory.TOUR, 12,
            FacilityCategory.CULTURE, 14,
            FacilityCategory.FESTIVAL, 15,
            FacilityCategory.LEISURE, 28,
            FacilityCategory.STAY, 32,
            FacilityCategory.SHOPPING, 38,
            FacilityCategory.RESTAURANT, 39,
            FacilityCategory.CAFE, 39
    );

    /**
     * @return 대응하는 분류. 적재 대상이 아닌 타입(여행코스 25, 교통 77 등)이면 {@code null}
     */
    public FacilityCategory map(
            String contentTypeId,
            String mediumCategoryCode
    ) {
        if (contentTypeId == null) {
            return null;
        }
        if (CONTENT_TYPE_FOOD.equals(contentTypeId)) {
            return MEDIUM_CATEGORY_CAFE.equals(mediumCategoryCode)
                    ? FacilityCategory.CAFE
                    : FacilityCategory.RESTAURANT;
        }
        return BY_CONTENT_TYPE.get(contentTypeId);
    }

    /**
     * 분류를 관광공사 조회 조건으로 옮긴다.
     *
     * @return 대응하는 {@code contentTypeId}. 분류를 지정하지 않았으면 {@code null}
     */
    public Integer toContentTypeId(FacilityCategory category) {
        return category == null ? null : TO_CONTENT_TYPE.get(category);
    }

    /**
     * 분류를 좁히는 데 필요한 분류체계 중분류를 준다.
     *
     * <p>카페만 해당한다. 음식점과 카페가 같은 {@code contentTypeId=39}를 쓰기 때문이다.
     *
     * @return 중분류 코드. 중분류로 좁힐 필요가 없으면 {@code null}
     */
    public String toMediumCategoryCode(FacilityCategory category) {
        return category == FacilityCategory.CAFE ? MEDIUM_CATEGORY_CAFE : null;
    }

    /**
     * 조회 결과에서 빼야 할 항목인지 본다.
     *
     * <p>음식점은 "카페가 아닌 39"인데 관광공사에 부정 조건이 없어 중분류로 좁히지 못한다.
     * 39 전체를 받아 카페를 여기서 걷어낸다.
     */
    public boolean isExcludedFrom(
            FacilityCategory category,
            String mediumCategoryCode
    ) {
        return category == FacilityCategory.RESTAURANT
                && MEDIUM_CATEGORY_CAFE.equals(mediumCategoryCode);
    }

}
