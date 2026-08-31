package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepets.domain.facility.entity.PetConditionStatus;

// status는 LLM의 주관적 판단이 아니라 unmappedConditionText 존재 여부로 기계적으로 결정된다 —
// 그 규칙만 검증한다. 실제 LLM 호출은 FacilityConditionLlmParserTest 참고(호출 여부만 검증,
// 비용이 드는 실제 API 호출은 하지 않음).
class FacilityConditionLlmParseResultTest {

    @Test
    void unmappedConditionText가_없으면_PARSED() {
        FacilityConditionExtraction extraction = new FacilityConditionExtraction(
                new BigDecimal("10.0"), false, List.of("목줄 착용"), List.of(), null, null
        );

        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.fromExtraction(extraction);

        assertThat(result.status()).isEqualTo(PetConditionStatus.PARSED);
        assertThat(result.unmappedConditionText()).isNull();
    }

    @Test
    void unmappedConditionText가_빈문자열이어도_PARSED() {
        FacilityConditionExtraction extraction = new FacilityConditionExtraction(
                null, false, List.of(), List.of(), null, "   "
        );

        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.fromExtraction(extraction);

        assertThat(result.status()).isEqualTo(PetConditionStatus.PARSED);
    }

    @Test
    void unmappedConditionText가_남아있으면_AMBIGUOUS() {
        FacilityConditionExtraction extraction = new FacilityConditionExtraction(
                null, false, List.of(), List.of(), null, "주말엔 사전 예약 필수(자세한 조건은 전화 문의)"
        );

        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.fromExtraction(extraction);

        assertThat(result.status()).isEqualTo(PetConditionStatus.AMBIGUOUS);
        assertThat(result.unmappedConditionText()).isEqualTo("주말엔 사전 예약 필수(자세한 조건은 전화 문의)");
    }

    @Test
    void unmappedConditionText에_한글이_없으면_할루시네이션으로_보고_PARSED() {
        // 관측된 실패 사례: Haiku가 이따금 원문과 무관한 숫자/영어 토큰을 채워넣는다.
        FacilityConditionExtraction extraction = new FacilityConditionExtraction(
                null, false, List.of(), List.of(), null, "+1.0"
        );

        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.fromExtraction(extraction);

        assertThat(result.status()).isEqualTo(PetConditionStatus.PARSED);
        assertThat(result.unmappedConditionText()).isNull();
    }

    @Test
    void unmappedConditionText가_자기_결과를_JSON으로_되풀이하면_할루시네이션으로_보고_PARSED() {
        // 관측된 실패 사례(시설 5807): 한글이 섞여 있어 한글 필터만으로는 못 잡는다.
        FacilityConditionExtraction extraction = new FacilityConditionExtraction(
                null, false, List.of("목줄 착용"), List.of("입마개 착용"), null,
                "{\"dangerousBreedRequiredItems\":[\"입마개 착용\"],\"isDangerousBreedExcluded\":false,"
                        + "\"unmappedConditionText\":null}"
        );

        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.fromExtraction(extraction);

        assertThat(result.status()).isEqualTo(PetConditionStatus.PARSED);
        assertThat(result.unmappedConditionText()).isNull();
    }

    @Test
    void unmappedConditionText에_한글이_섞여있으면_그대로_AMBIGUOUS() {
        // SNS, 24h처럼 영어/숫자가 섞여도 한글 원문 문구면 진짜 잔여 조건일 수 있으니 살린다.
        FacilityConditionExtraction extraction = new FacilityConditionExtraction(
                null, false, List.of(), List.of(), null, "SNS 인증 시 24h 이내 재방문 불가"
        );

        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.fromExtraction(extraction);

        assertThat(result.status()).isEqualTo(PetConditionStatus.AMBIGUOUS);
        assertThat(result.unmappedConditionText()).isEqualTo("SNS 인증 시 24h 이내 재방문 불가");
    }

    @Test
    void requiredItems가_null이면_빈리스트로_대체() {
        FacilityConditionExtraction extraction = new FacilityConditionExtraction(
                null, false, null, null, null, null
        );

        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.fromExtraction(extraction);

        assertThat(result.requiredItems()).isEmpty();
        assertThat(result.dangerousBreedRequiredItems()).isEmpty();
    }

    @Test
    void ambiguousWithoutText는_AMBIGUOUS_상태와_전달한_텍스트를_그대로_반환() {
        FacilityConditionLlmParseResult result =
                FacilityConditionLlmParseResult.ambiguousWithoutText("일부구역 동반가능 — 구체적인 동반 가능 구역 설명 없음");

        assertThat(result.status()).isEqualTo(PetConditionStatus.AMBIGUOUS);
        assertThat(result.unmappedConditionText()).isEqualTo("일부구역 동반가능 — 구체적인 동반 가능 구역 설명 없음");
        assertThat(result.maxWeight()).isNull();
        assertThat(result.requiredItems()).isEmpty();
        assertThat(result.dangerousBreedRequiredItems()).isEmpty();
    }

    @Test
    void noCondition은_NO_CONDITION_상태를_반환() {
        FacilityConditionLlmParseResult result = FacilityConditionLlmParseResult.noCondition();

        assertThat(result.status()).isEqualTo(PetConditionStatus.NO_CONDITION);
        assertThat(result.requiredItems()).isEmpty();
        assertThat(result.dangerousBreedRequiredItems()).isEmpty();
    }
}
