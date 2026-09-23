package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anthropic.client.AnthropicClient;
import com.freepets.domain.facility.entity.PetConditionStatus;

// 실제 Claude 호출은 비용이 들어서 여기서 하지 않는다 — "조건 원문이 전부 비어있으면 LLM을
// 아예 호출하지 않는다"는 비용 절감 경로와, 반대로 "원문이 있으면 반드시 호출한다"는 경계만
// 검증한다. 실제 호출 결과 확인은 수동 검증(anthropicProbe 같은 별도 태스크)으로 한다.
@ExtendWith(MockitoExtension.class)
class FacilityConditionLlmParserTest {

    @Mock
    private AnthropicClient anthropicClient;

    @InjectMocks
    private FacilityConditionLlmParser facilityConditionLlmParser;

    @Test
    void 조건_원문이_전부_비어있으면_LLM을_호출하지_않고_NO_CONDITION() {
        FacilityConditionLlmParseResult result =
                facilityConditionLlmParser.parse(null, "", "   ", null, null, null);

        assertThat(result.status()).isEqualTo(PetConditionStatus.NO_CONDITION);
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void 나머지가_비어있고_accompanyType이_전구역이면_LLM을_호출하지_않고_NO_CONDITION() {
        // accompanyType만 채워져 있고 구조화할 실질 문장이 없는 케이스 — accompanyType까지
        // isAllBlank에 넣으면 이 시설들이 전부 불필요하게 LLM을 탄다.
        FacilityConditionLlmParseResult result =
                facilityConditionLlmParser.parse("전구역 동반가능", null, null, null, null, null);

        assertThat(result.status()).isEqualTo(PetConditionStatus.NO_CONDITION);
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void 나머지가_비어있고_accompanyType이_일부구역이면_LLM을_호출하지_않고_AMBIGUOUS() {
        // 어느 구역인지 설명이 없는 "일부구역 동반가능"류(실측 34건) — 조건 없음으로 단정하면
        // 판별 엔진이 제약 없음으로 오판한다. LLM에 넘겨도 구조화할 문장이 없으니 호출 없이
        // 기계적으로 AMBIGUOUS로 남긴다.
        FacilityConditionLlmParseResult result =
                facilityConditionLlmParser.parse("일부구역 동반가능", null, null, null, null, null);

        assertThat(result.status()).isEqualTo(PetConditionStatus.AMBIGUOUS);
        assertThat(result.unmappedConditionText()).contains("일부구역 동반가능");
        // partialAreaNote도 같이 채워야 한다 — PetCheckJudgeService는 petConditionStatus나
        // unmappedConditionText를 안 읽고 partialAreaNote만 사용자 안내에 반영하므로, 이게
        // 비어있으면 이 시설은 조용히 ALLOWED로 나가 "일부 구역만 가능하다"는 신호가 유실된다.
        assertThat(result.partialAreaNote()).isNotBlank();
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void 관광공사_원문이_비어도_정리_안내문이_있으면_LLM을_호출한다() {
        // #148 회귀 방지 — 관광공사 원문 4종만 보고 판단하면 조건이 멀쩡히 적힌 시설 8,600건이
        // NO_CONDITION으로 버려졌다. 깊은 스텁이라 응답 본문을 못 꺼내 실패하는데, 그 실패
        // 자체가 기계적 분기로 새지 않고 Claude까지 갔다는 증거다.
        AnthropicClient deepStubClient = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        FacilityConditionLlmParser parser = new FacilityConditionLlmParser(deepStubClient);

        assertThatThrownBy(() -> parser.parse(
                "전구역 동반가능", null, null, null, null,
                "일부 구역에 한해 9kg 이하 반려동물과 함께 이용할 수 있습니다."
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 프롬프트에_정리_안내문이_실린다() {
        String userMessage = FacilityConditionLlmParser.buildUserMessage(
                "전구역 동반가능", null, null, null, null,
                "일부 구역에 한해 9kg 이하 반려동물과 함께 이용할 수 있습니다."
        );

        assertThat(userMessage)
                .contains("정리된 조건 안내문: 일부 구역에 한해 9kg 이하 반려동물과 함께 이용할 수 있습니다.");
    }
}
