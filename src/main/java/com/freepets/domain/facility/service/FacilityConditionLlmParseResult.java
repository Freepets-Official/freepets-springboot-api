package com.freepets.domain.facility.service;

import java.math.BigDecimal;
import java.util.List;

import com.freepets.domain.facility.entity.PetConditionStatus;

/**
 * {@link FacilityConditionLlmParser}의 결과. {@code status}는 LLM의 주관적 판단이 아니라
 * {@code unmappedConditionText} 존재 여부로 기계적으로 결정된다 — {@link FacilityConditionLlmParser}
 * §status 결정 참고.
 */
public record FacilityConditionLlmParseResult(
        PetConditionStatus status,
        BigDecimal maxWeight,
        boolean dangerousBreedExcluded,
        List<String> requiredItems,
        String partialAreaNote,
        String unmappedConditionText
) {

    public static FacilityConditionLlmParseResult noCondition() {
        return new FacilityConditionLlmParseResult(
                PetConditionStatus.NO_CONDITION, null, false, List.of(), null, null
        );
    }

    public static FacilityConditionLlmParseResult fromExtraction(FacilityConditionExtraction extraction) {
        boolean hasUnmapped = extraction.unmappedConditionText() != null
                && !extraction.unmappedConditionText().isBlank();

        return new FacilityConditionLlmParseResult(
                hasUnmapped ? PetConditionStatus.AMBIGUOUS : PetConditionStatus.PARSED,
                extraction.maxWeight(),
                extraction.dangerousBreedExcluded(),
                extraction.requiredItems() != null ? extraction.requiredItems() : List.of(),
                extraction.partialAreaNote(),
                hasUnmapped ? extraction.unmappedConditionText() : null
        );
    }
}
