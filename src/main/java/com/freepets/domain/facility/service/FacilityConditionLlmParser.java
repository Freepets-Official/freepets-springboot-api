package com.freepets.domain.facility.service;

import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;

import lombok.RequiredArgsConstructor;

/**
 * LLM(Claude Haiku 4.5)으로 시설 조건 원문을 최초 1회 구조화한다. 이후 판별
 * ({@code PetCheckJudgeService})은 이 결과가 채운 {@code Facility} 컬럼만 읽고, 판별할 때마다
 * 재호출하지 않는다.
 *
 * <p>실측 데이터(facility_pet_db.xlsx, 9,678건) 기준 약 90%는 조건 원문 자체가 없어 LLM을
 * 아예 호출하지 않고 {@code NO_CONDITION}으로 바로 결정한다(docs/03-ai-prompts.md 상단 표 참고).
 *
 * <p>{@code status}는 LLM이 "애매한지"를 주관적으로 판단하는 게 아니라,
 * {@code unmappedConditionText}(컬럼으로 못 담는 잔여 텍스트)가 남아있는지로 기계적으로
 * 결정한다 — {@link FacilityConditionLlmParseResult#fromExtraction} 참고.
 *
 * <p>새 시설 동기화(lazy-sync)에서 이 파서를 언제·어떻게 호출할지는 이 클래스 범위 밖이다 — 별도 이슈.
 */
@Service
@RequiredArgsConstructor
public class FacilityConditionLlmParser {

    private static final String MODEL = "claude-haiku-4-5";
    private static final long MAX_TOKENS = 2048L;

    private static final String SYSTEM_PROMPT = """
            너는 반려동물 동반 여행지의 조건 원문을 구조화된 데이터로 변환하는 파서다.
            추측하지 말고 원문에 실제로 적힌 내용만 반영해라. 원문에 없는 조건을 만들어내지 마라.
            체중 상한처럼 숫자가 명시되지 않은 정성적 표현(예: "소형견만")은 컬럼에 숫자를 지어내지
            말고 unmappedConditionText로 그대로 남겨라.
            """;

    private final AnthropicClient anthropicClient;

    public FacilityConditionLlmParseResult parse(
            String accompanyType,
            String allowedAnimalText,
            String requiredMatterText,
            String etcAccompanyText,
            String accidentRiskText
    ) {
        if (isAllBlank(accompanyType, allowedAnimalText, requiredMatterText, etcAccompanyText, accidentRiskText)) {
            return FacilityConditionLlmParseResult.noCondition();
        }

        FacilityConditionExtraction extraction = extract(
                accompanyType, allowedAnimalText, requiredMatterText, etcAccompanyText, accidentRiskText
        );

        return FacilityConditionLlmParseResult.fromExtraction(extraction);
    }

    private FacilityConditionExtraction extract(
            String accompanyType,
            String allowedAnimalText,
            String requiredMatterText,
            String etcAccompanyText,
            String accidentRiskText
    ) {
        StructuredMessageCreateParams<FacilityConditionExtraction> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .outputConfig(FacilityConditionExtraction.class)
                .addUserMessage(buildUserMessage(
                        accompanyType, allowedAnimalText, requiredMatterText, etcAccompanyText, accidentRiskText
                ))
                .build();

        return anthropicClient.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(typed -> typed.text())
                .orElseThrow(() -> new IllegalStateException("Claude 응답에 구조화 결과가 없습니다."));
    }

    private String buildUserMessage(
            String accompanyType,
            String allowedAnimalText,
            String requiredMatterText,
            String etcAccompanyText,
            String accidentRiskText
    ) {
        return """
                동반 구분: %s
                동반 가능 동물: %s
                동반 시 필요사항: %s
                기타 동반 정보: %s
                사고 대비사항: %s
                """.formatted(
                nullToDash(accompanyType),
                nullToDash(allowedAnimalText),
                nullToDash(requiredMatterText),
                nullToDash(etcAccompanyText),
                nullToDash(accidentRiskText)
        );
    }

    private String nullToDash(String text) {
        return (text == null || text.isBlank()) ? "-" : text;
    }

    private boolean isAllBlank(String... texts) {
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
