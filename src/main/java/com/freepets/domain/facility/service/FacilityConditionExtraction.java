package com.freepets.domain.facility.service;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM 구조화 출력 스키마. {@link FacilityConditionLlmParser}가 이 레코드로 Claude를 호출해
 * 시설 조건 원문을 컬럼으로 뽑아낸다.
 *
 * <p>필드 설명(Jackson {@code @JsonPropertyDescription})이 곧 프롬프트다 — 값을 바꾸면
 * 추출 결과가 바뀐다.
 */
public record FacilityConditionExtraction(

        @JsonPropertyDescription(
                "동반 가능한 최대 체중(kg). 원문에 명시적인 체중 상한이 없으면 null. "
                        + "'소형견만' 같은 표현만 있고 숫자가 없으면 추정하지 말고 null로 둔다 — "
                        + "그런 표현은 unmappedConditionText로 넘긴다."
        )
        BigDecimal maxWeight,

        @JsonPropertyDescription(
                "맹견(도사견, 핏불 등 동물보호법상 위험 품종) 동반이 원문에 명시적으로 제외돼 있으면 true."
        )
        boolean isDangerousBreedExcluded,

        @JsonPropertyDescription(
                "출입 시 지켜야 할 준비물·행동 조건을 사람이 읽을 수 있는 한국어 문구로 나열. "
                        + "예: [\"목줄 착용\", \"이동장(켄넬) 사용\"]. 없으면 빈 배열."
        )
        List<String> requiredItems,

        @JsonPropertyDescription(
                "맹견(도사견, 핏불 등 동물보호법상 위험 품종)일 때만 추가로 지켜야 하는 조건 — "
                        + "requiredItems(전체 방문객 대상)와 구분해서, 맹견이 아니면 안 지켜도 되는 것만 담는다. "
                        + "예: 원문이 '맹견의 경우, 입마개 착용 필수'면 [\"입마개 착용\"]. 없으면 빈 배열."
        )
        List<String> dangerousBreedRequiredItems,

        @JsonPropertyDescription(
                "일부 구역에서만 동반 가능하다면 그 구역을 설명하는 문구(예: \"야외 테라스석에 한함\"). "
                        + "전 구역 동반 가능하거나 구역 제한 언급이 없으면 null."
        )
        String partialAreaNote,

        @JsonPropertyDescription(
                "위 컬럼 중 어디에도 담을 수 없는 조건 문장이 원문에 남아 있으면 그 문장을 그대로 옮긴다. "
                        + "판단을 요약하거나 재구성하지 말고 원문 그대로. 원문 내용이 전부 위 컬럼에 담겼다면 "
                        + "반드시 null을 반환한다 — 이 필드를 비워두는 게 흔한 정답이며, +1.0 같은 숫자나 "
                        + "영어 단어처럼 원문에 없는 값을 지어내서 채우면 안 된다."
        )
        String unmappedConditionText

) {
}
