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
}
