package com.freepets.domain.facility.dto;

import java.math.BigDecimal;
import java.util.List;

import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

public class FacilityResponseDTO {

    private FacilityResponseDTO() {}

    /**
     * 시설 목록 한 건.
     *
     * <p>{@code distanceM}·{@code reviewCnt}는 다른 네이밍 규칙과 달리 줄여 썼다.
     * 프론트가 코딩하는 계약이라 API 명세서 표기를 그대로 따랐다.
     */
    public record FacilitySummary(
            Long facilityId,
            String name,
            FacilityCategory category,
            String address,
            long distanceM,
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            List<Requirement> requirements,

            // 친화도 집계가 아직 없어 당분간 항상 null이다. 리뷰 API가 붙으면 이 API 수정 없이 채워진다.
            Integer petScore,
            String rating,
            long reviewCnt
    ) {}

    public record FacilitySearchResult(
            List<FacilitySummary> items,
            long total
    ) {}
}
