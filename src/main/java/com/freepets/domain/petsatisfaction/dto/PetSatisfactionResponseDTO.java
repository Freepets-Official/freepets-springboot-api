package com.freepets.domain.petsatisfaction.dto;

import java.util.List;

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
}
