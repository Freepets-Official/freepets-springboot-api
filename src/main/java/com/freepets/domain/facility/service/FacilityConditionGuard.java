package com.freepets.domain.facility.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.freepets.domain.facility.entity.Facility;

import lombok.extern.slf4j.Slf4j;

/**
 * LLM이 뽑은 {@code maxWeight}가 원문 근거 없이 지어낸 값인지 검사해 버린다.
 * {@link FacilityConditionLlmBatchService}(동기 경로)와 {@link FacilityConditionLlmBatchApiService}
 * (Batch API 경로, #39) 둘 다 이 방어를 통과해야 한다 — 따로 두면 두 경로의 결과가 갈린다.
 */
@Slf4j
final class FacilityConditionGuard {

    /**
     * {@code Facility.maxWeight}는 {@code numeric(5,2)} 컬럼이라 절댓값이 이 값 이상이면 저장이
     * 통째로 실패한다(엔티티 전체를 한 번에 UPDATE하므로 이 필드 하나 때문에 이번에 LLM이
     * 제대로 뽑아낸 다른 필드까지 다 같이 유실된다). facility_id 150처럼 규칙 엔진(#22)이
     * 예전에 잘못 뽑아 이미 DB에 들어있던 값이 원인일 수 있다 — 근본 원인 조사는 별개고,
     * 그 사이 최소한 나머지 파싱 결과라도 저장되도록 범위를 벗어난 값은 버린다.
     */
    private static final BigDecimal MAX_WEIGHT_COLUMN_LIMIT = new BigDecimal("1000");

    /**
     * 원문에 실제 체중 언급(숫자+kg류 단위)이 있는지 본다. {@code PetConditionParser.WEIGHT_LIMIT}과
     * 달리 "이하/미만" 같은 상한 표현까지는 요구하지 않는다 — maxWeight를 뒷받침할 근거가
     * 원문에 있기는 한지만 확인하는 용도라, 상한이 아닌 다른 표현(예: "10kg 이상")도 일단
     * 근거로 인정한다.
     */
    private static final Pattern WEIGHT_MENTION = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:kg|㎏|킬로그램|킬로)");

    private FacilityConditionGuard() {}

    /**
     * LLM이 직접 뽑은 maxWeight만 원문 근거를 검사한다 — 규칙 엔진(#22) 값은 그 자체로 실제
     * kg 매칭에서만 나오는 신뢰된 값이라 이 검사 대상이 아니다(재검증하면 규칙 엔진이 이미
     * 신뢰하기로 한 값을 LLM 출력물과 같은 기준으로 다시 의심하게 된다). 규칙 엔진 값이 있으면
     * 그 값으로 LLM 판독값을 대체해서 우선시킨다.
     */
    static FacilityConditionLlmParseResult apply(
            Facility facility,
            FacilityConditionLlmParseResult parsed
    ) {
        parsed = rejectMaxWeightWithoutSourceEvidence(facility, parsed);

        if (facility.getMaxWeight() != null) {
            parsed = parsed.withMaxWeight(facility.getMaxWeight(), facility.getMaxWeightInclusive());
        }

        return rejectOutOfRangeMaxWeight(facility, parsed);
    }

    private static FacilityConditionLlmParseResult rejectOutOfRangeMaxWeight(
            Facility facility,
            FacilityConditionLlmParseResult parsed
    ) {
        BigDecimal maxWeight = parsed.maxWeight();
        if (maxWeight != null && maxWeight.abs().compareTo(MAX_WEIGHT_COLUMN_LIMIT) >= 0) {
            log.warn(
                    "시설 {} maxWeight({})가 컬럼 범위(절댓값 {} 미만)를 벗어나 버립니다 — 원인 조사 필요",
                    facility.getFacilityId(), maxWeight, MAX_WEIGHT_COLUMN_LIMIT
            );
            return parsed.withoutMaxWeight();
        }
        return parsed;
    }

    /**
     * 원문에 "kg" 언급이 전혀 없는데 maxWeight가 채워져 있으면 버린다. 규칙 엔진(#22)이 뽑은
     * 값은 애초에 실제 kg 매칭에서만 나오니 걸릴 일이 없고, 이건 LLM이 "추측하지 말라"는
     * 지시를 어기고 관련 지식(예: 일반적인 맹견 기준)으로 숫자를 지어내는 사례를 막기 위한
     * 것이다. 값이 0~200 범위 안이라 {@link #rejectOutOfRangeMaxWeight}로는 못 잡는다.
     */
    private static FacilityConditionLlmParseResult rejectMaxWeightWithoutSourceEvidence(
            Facility facility,
            FacilityConditionLlmParseResult parsed
    ) {
        if (parsed.maxWeight() == null || hasWeightMention(facility)) {
            return parsed;
        }

        log.warn(
                "시설 {} maxWeight({})가 원문에 체중 언급 없이 나와 버립니다 — LLM 추측 의심",
                facility.getFacilityId(), parsed.maxWeight()
        );
        return parsed.withoutMaxWeight();
    }

    static boolean hasWeightMention(Facility facility) {
        String sourceText = String.join(" ",
                nullToEmpty(facility.getAccompanyType()),
                nullToEmpty(facility.getAllowedAnimalText()),
                nullToEmpty(facility.getRequiredMatterText()),
                nullToEmpty(facility.getEtcAccompanyText()),
                nullToEmpty(facility.getAccidentRiskText())
        );
        return WEIGHT_MENTION.matcher(sourceText).find();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
