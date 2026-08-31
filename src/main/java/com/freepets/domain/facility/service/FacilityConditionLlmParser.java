package com.freepets.domain.facility.service;

import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
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

    // FacilityConditionLlmBatchApiService(#39)가 Batch API 요청을 만들 때도 동일한 모델·프롬프트·
    // 스키마를 써야 해서 패키지 접근으로 열어둔다 — private로 막으면 배치 쪽에서 통째로
    // 복붙하게 되고, 둘이 갈라지면 동기 경로와 배치 경로의 파싱 결과가 서로 달라진다.
    static final String MODEL = "claude-haiku-4-5";

    // 2048이었을 때 실측 배치(832건 중 3건)에서 응답이 max_tokens 한도에 걸려 JSON이 채
    // 끝나기 전에 잘렸다("Unexpected end-of-input in character escape sequence") — 원문이
    // 유난히 긴 시설에서 구조화 출력이 2048 토큰을 넘어간 것으로 보인다. 여유를 두고 올린다.
    static final long MAX_TOKENS = 4096L;

    /**
     * 관광공사 {@code acmpyTypeCd}의 두 원자값 중 하나. 실측 데이터 기준 이 값이면 구역 제한이
     * 없다는 뜻이라, 나머지 4종 원문이 전부 비어 있을 때 "조건 없음"으로 확정할 수 있다
     * (PetConditionParser 클래스 주석 §"acmpyTypeCd가 2개뿐인 코드성 필드" 참고).
     */
    private static final String UNRESTRICTED_ACCOMPANY_TYPE = "전구역 동반가능";

    static final String SYSTEM_PROMPT = """
            너는 반려동물 동반 여행지의 조건 원문을 구조화된 데이터로 변환하는 파서다.
            추측하지 말고 원문에 실제로 적힌 내용만 반영해라. 원문에 없는 조건을 만들어내지 마라.
            체중 상한처럼 숫자가 명시되지 않은 정성적 표현(예: "소형견만")은 컬럼에 숫자를 지어내지
            말고 unmappedConditionText로 그대로 남겨라.
            모든 필드를 채워야 한다는 압박을 느끼지 마라 — 원문에 없는 내용이면 해당 필드는
            비워두는(null 또는 빈 배열) 것이 정답이다. 특히 unmappedConditionText는 원문 내용이
            이미 다른 컬럼에 전부 반영됐다면 반드시 null이어야 하고, +1.0 같은 숫자나 영어
            단어처럼 원문에 없는 값을 채워넣으면 절대 안 된다.

            예시: "동반 시 필요사항"에 "목줄 착용 필수. 맹견은 입마개 착용"이 적혀 있다면
            requiredItems=["목줄 착용"], dangerousBreedRequiredItems=["입마개 착용"]로 나누어 담고,
            원문이 이 두 필드로 전부 소화됐으므로 unmappedConditionText는 null이다 — 원문 텍스트가
            길다고 해서 unmappedConditionText에 뭔가 남아있어야 하는 건 아니다.

            잘못된 예시(하지 말아야 할 것): 원문 어디에도 없는데 unmappedConditionText에
            "+1.0"이나 영어 단어를 채워넣는 것. 그런 값을 만들어내느니 null을 반환해라.
            또한 unmappedConditionText에 너 자신이 방금 만든 구조화 결과를 JSON 문자열로
            요약해서 다시 채워넣지 마라(예: {"dangerousBreedRequiredItems":[...]} 같은 값).
            이 필드는 원문에서 못 담은 잔여 텍스트 전용이지, 결과 요약을 담는 곳이 아니다.
            unmappedConditionText와 partialAreaNote에 {maxWeight} 같은 필드 이름이나 변수
            이름을 중괄호와 함께 그대로 채워넣지 마라 — 원문에 없는 값이면 그 필드는 null이다.
            """;

    private final AnthropicClient anthropicClient;

    public FacilityConditionLlmParseResult parse(
            String accompanyType,
            String allowedAnimalText,
            String requiredMatterText,
            String etcAccompanyText,
            String accidentRiskText
    ) {
        // accompanyType(동반구분)은 실측상 "전구역 동반가능"/"일부구역 동반가능" 두 값뿐인
        // 코드성 필드라 그 자체로는 구조화할 실질 문장이 없다 — isAllBlank 판정에서 뺀다.
        // 나머지 4종(동반가능동물·필수준비물·기타·사고대비)이 전부 비어 있으면 LLM을 부를
        // 이유가 없다: 89.4%(약 8,687건, "전구역 동반가능")는 조건 없음이고, 나머지는
        // accompanyType만으로 결정한다.
        if (isAllBlank(allowedAnimalText, requiredMatterText, etcAccompanyText, accidentRiskText)) {
            return resolveByAccompanyTypeOnly(accompanyType);
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

    static String buildUserMessage(
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

    private static String nullToDash(String text) {
        return (text == null || text.isBlank()) ? "-" : text;
    }

    /**
     * Batch API 요청(#39)에서 쓸 {@code OutputConfig}를 만든다. {@code outputConfig(Class)}는
     * {@code StructuredMessageCreateParams.Builder}만 내주고 {@code OutputConfig} 자체를 직접
     * 만드는 API는 없어서, 더미 값으로 정상적인 요청 하나를 만들어 그 안에서 꺼낸다 — 이렇게
     * 뽑은 스키마는 요청마다 항상 동일하므로 배치 전체에서 한 번만 만들어 재사용하면 된다.
     */
    static OutputConfig buildOutputConfig() {
        return MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .addUserMessage("placeholder")
                .outputConfig(FacilityConditionExtraction.class)
                .build()
                .rawParams()
                .outputConfig()
                .orElseThrow(() -> new IllegalStateException("outputConfig 생성에 실패했습니다."));
    }

    private boolean isAllBlank(String... texts) {
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 나머지 4종이 전부 비어 있을 때 accompanyType만으로 상태를 기계적으로 정한다. 실측 데이터
     * 기준 값은 두 종류뿐이다: "전구역 동반가능"(구역 제한 없음)이거나 "일부구역 동반가능"류
     * (제한은 있는데 어느 구역인지 설명이 없음). 전자는 조건 없음, 후자는 사람이 확인해야 할
     * 신호로 AMBIGUOUS에 남긴다 — LLM에 넘겨도 구조화할 실질 문장이 없어 호출하지 않는다.
     */
    private FacilityConditionLlmParseResult resolveByAccompanyTypeOnly(String accompanyType) {
        String normalized = accompanyType == null ? "" : accompanyType.trim();

        if (normalized.isEmpty() || normalized.equals(UNRESTRICTED_ACCOMPANY_TYPE)) {
            return FacilityConditionLlmParseResult.noCondition();
        }

        // partialAreaNote도 같이 채운다 — 안 채우면 이 시설이 PetCheckJudgeService의
        // 사용자 안내(applicableConditions)에 전혀 안 잡히고 조용히 ALLOWED로 나간다.
        // petConditionStatus/unmappedConditionText는 PetCheckJudgeService가 안 읽으므로
        // AMBIGUOUS로 남겨두는 것만으로는 사용자에게 아무 신호가 안 간다.
        return FacilityConditionLlmParseResult.ambiguousWithoutText(
                "일부 구역에서만 동반 가능 — 세부 안내 없음, 방문 전 확인 필요",
                normalized + " — 구체적인 동반 가능 구역 설명 없음"
        );
    }
}
