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
        boolean isDangerousBreedExcluded,
        List<String> requiredItems,
        List<String> dangerousBreedRequiredItems,
        String partialAreaNote,
        String unmappedConditionText
) {

    public static FacilityConditionLlmParseResult noCondition() {
        return new FacilityConditionLlmParseResult(
                PetConditionStatus.NO_CONDITION, null, false, List.of(), List.of(), null, null
        );
    }

    /**
     * maxWeight만 교체한 복사본을 만든다. 규칙 엔진(PetConditionParser, #22)이 이미 뽑아낸
     * maxWeight가 있으면 이 값으로 LLM의 판독값을 대체해서 우선시킨다 — 나머지 필드(맹견 배제,
     * 요구조건, 잔여 텍스트)는 LLM이 채운 그대로 둔다. {@code FacilityConditionLlmBatchService} 참고.
     */
    public FacilityConditionLlmParseResult withMaxWeight(BigDecimal overrideMaxWeight) {
        return new FacilityConditionLlmParseResult(
                status, overrideMaxWeight, isDangerousBreedExcluded(), requiredItems(),
                dangerousBreedRequiredItems(), partialAreaNote(), unmappedConditionText()
        );
    }

    public static FacilityConditionLlmParseResult fromExtraction(FacilityConditionExtraction extraction) {
        boolean hasUnmapped = extraction.unmappedConditionText() != null
                && !extraction.unmappedConditionText().isBlank();

        return new FacilityConditionLlmParseResult(
                hasUnmapped ? PetConditionStatus.AMBIGUOUS : PetConditionStatus.PARSED,
                extraction.maxWeight(),
                extraction.isDangerousBreedExcluded(),
                extraction.requiredItems() != null ? extraction.requiredItems() : List.of(),
                extraction.dangerousBreedRequiredItems() != null ? extraction.dangerousBreedRequiredItems() : List.of(),
                extraction.partialAreaNote(),
                hasUnmapped ? extraction.unmappedConditionText() : null
        );
    }
}
