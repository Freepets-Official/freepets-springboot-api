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
     * LLM을 부르지 않고 AMBIGUOUS로 확정한다. {@link FacilityConditionLlmParser}가 accompanyType
     * 하나만으로("일부구역 동반가능"인데 설명이 없는 경우) 판단할 때 쓴다 — 구조화할 실질
     * 문장이 없으니 호출할 이유가 없지만, 조건 없음으로 단정하면 안 되는 경우다.
     */
    public static FacilityConditionLlmParseResult ambiguousWithoutText(String unmappedConditionText) {
        return new FacilityConditionLlmParseResult(
                PetConditionStatus.AMBIGUOUS, null, false, List.of(), List.of(), null, unmappedConditionText
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
        String unmappedConditionText = sanitizeUnmappedConditionText(extraction.unmappedConditionText());
        boolean hasUnmapped = unmappedConditionText != null;

        return new FacilityConditionLlmParseResult(
                hasUnmapped ? PetConditionStatus.AMBIGUOUS : PetConditionStatus.PARSED,
                extraction.maxWeight(),
                extraction.isDangerousBreedExcluded(),
                extraction.requiredItems() != null ? extraction.requiredItems() : List.of(),
                extraction.dangerousBreedRequiredItems() != null ? extraction.dangerousBreedRequiredItems() : List.of(),
                extraction.partialAreaNote(),
                unmappedConditionText
        );
    }

    /**
     * unmappedConditionText는 원문 조건 문구를 그대로 옮긴 값이어야 하는데, 관측 결과 Haiku가
     * 이따금 원문과 무관한 문자열을 채워넣는다. 두 가지 실패 패턴이 확인됐다:
     * <ul>
     *   <li>{@code "+1.0"}, 영어 토큰처럼 원문에 없는 값 — 원문은 전부 한국어라 실제 잔여
     *       조건이면 한글이 반드시 섞여 있으므로, 한글이 하나도 없으면 걸러낸다.</li>
     *   <li>자기 자신의 구조화 결과를 JSON 문자열로 되풀이해서 이 필드 안에 또 채워넣는 경우
     *       (예: {@code {"dangerousBreedRequiredItems":[...],...}}) — 한글이 섞여 있어 위
     *       필터로는 못 잡으므로, JSON 객체처럼 생긴 값인지 따로 확인한다.</li>
     * </ul>
     * 그래야 이미 다 파싱된 시설이 이 값 하나 때문에 AMBIGUOUS로 잘못 분류되는 걸 막을 수
     * 있다(status 결정 로직 참고).
     */
    private static String sanitizeUnmappedConditionText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        String trimmed = rawText.trim();
        boolean hasHangul = trimmed.codePoints()
                .anyMatch(codePoint -> codePoint >= 0xAC00 && codePoint <= 0xD7A3);
        boolean looksLikeJsonObject = trimmed.startsWith("{") && trimmed.endsWith("}");

        return (hasHangul && !looksLikeJsonObject) ? rawText : null;
    }
}
