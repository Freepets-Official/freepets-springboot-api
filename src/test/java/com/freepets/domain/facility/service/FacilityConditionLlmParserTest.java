package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anthropic.client.AnthropicClient;
import com.freepets.domain.facility.entity.PetConditionStatus;

// 실제 Claude 호출은 비용이 들어서 여기서 하지 않는다 — "조건 원문이 전부 비어있으면 LLM을
// 아예 호출하지 않는다"는 비용 절감 경로만 검증한다(실측 데이터 기준 약 90% 케이스).
// 실제 호출 경로 확인은 수동 검증(anthropicProbe 같은 별도 태스크, 이 이슈 범위 밖)으로 한다.
@ExtendWith(MockitoExtension.class)
class FacilityConditionLlmParserTest {

    @Mock
    private AnthropicClient anthropicClient;

    @InjectMocks
    private FacilityConditionLlmParser facilityConditionLlmParser;

    @Test
    void 조건_원문이_전부_비어있으면_LLM을_호출하지_않고_NO_CONDITION() {
        FacilityConditionLlmParseResult result = facilityConditionLlmParser.parse(null, "", "   ", null, null);

        assertThat(result.status()).isEqualTo(PetConditionStatus.NO_CONDITION);
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void 나머지_4종이_비어있고_accompanyType이_전구역이면_LLM을_호출하지_않고_NO_CONDITION() {
        // 실측 데이터 89.4%가 이 케이스다 — accompanyType만 채워져 있고 구조화할 실질 문장이
        // 없는데, accompanyType까지 isAllBlank에 넣으면 이 90%가 전부 불필요하게 LLM을 탄다.
        FacilityConditionLlmParseResult result =
                facilityConditionLlmParser.parse("전구역 동반가능", null, null, null, null);

        assertThat(result.status()).isEqualTo(PetConditionStatus.NO_CONDITION);
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void 나머지_4종이_비어있고_accompanyType이_일부구역이면_LLM을_호출하지_않고_AMBIGUOUS() {
        // 어느 구역인지 설명이 없는 "일부구역 동반가능"류(실측 34건) — 조건 없음으로 단정하면
        // 판별 엔진이 제약 없음으로 오판한다. LLM에 넘겨도 구조화할 문장이 없으니 호출 없이
        // 기계적으로 AMBIGUOUS로 남긴다.
        FacilityConditionLlmParseResult result =
                facilityConditionLlmParser.parse("일부구역 동반가능", null, null, null, null);

        assertThat(result.status()).isEqualTo(PetConditionStatus.AMBIGUOUS);
        assertThat(result.unmappedConditionText()).contains("일부구역 동반가능");
        verifyNoInteractions(anthropicClient);
    }
}
