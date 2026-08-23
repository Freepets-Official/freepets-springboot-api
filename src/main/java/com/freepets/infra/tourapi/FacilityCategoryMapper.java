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

}
