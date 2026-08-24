package com.freepets.domain.petsatisfaction.dto;

import java.util.List;

import com.freepets.domain.facility.entity.FacilityCategory;

public class PetSatisfactionResponseDTO {

    private PetSatisfactionResponseDTO() {}

    public record UpsertResult(
            Long petId,
            Long facilityId,
            float score
    ) {}

    /** 시설 하나 기준, 내 반려동물 전체(기록 전 포함) 만족도. "우리 아이" 탭 화면용. */
    public record FacilityItem(
            Long petId,
            String petName,
            Float score,
            boolean isRecorded
    ) {}

    public record FacilitySatisfactionList(
            List<FacilityItem> items
    ) {}

    /** 홈 "아이별 좋아한 곳 TOP" 카드 하나(시설 1건)에 해당. */
    public record TopFacility(
            Long facilityId,
            String facilityName,
            FacilityCategory category,
            float score
    ) {}

    /** 반려동물 하나가 좋아한 곳 TOP 3. 기록이 하나도 없는 반려동물은 응답에 아예 나오지 않는다. */
    public record PetTopFacilities(
            Long petId,
            String petName,
            List<TopFacility> topFacilities
    ) {}

    public record MySatisfactionList(
            List<PetTopFacilities> pets
    ) {}
}
