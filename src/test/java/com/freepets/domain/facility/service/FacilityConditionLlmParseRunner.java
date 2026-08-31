package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code petConditionStatus = NOT_PROCESSED}인 시설을 실제로 파싱하는 실행기.
 *
 * <p>시설마다 Claude를 호출할 수 있어(조건 원문이 있는 약 10%) {@code test} 태스크에서는
 * 실행되지 않는다. 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilityConditionParse
 * </pre>
 *
 * <p>인증키는 {@code application.yml}의 {@code anthropic.api-key}에서 읽는다.
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "facility.condition.parse",
        matches = "true",
        disabledReason = "조건 파싱 전용 실행기. ./gradlew facilityConditionParse 로 실행한다."
)
class FacilityConditionLlmParseRunner {

    @Autowired
    private FacilityConditionLlmBatchService facilityConditionLlmBatchService;

    @Test
    @DisplayName("NOT_PROCESSED 시설의 조건 원문을 파싱한다")
    @DisabledIfSystemProperty(
            named = "facility.condition.parse.withCondition",
            matches = "true",
            disabledReason = "조건 원문이 있는 시설만 골라 검증하는 실행(facilityConditionParseSampleWithCondition)에서는 건너뛴다."
    )
    void NOT_PROCESSED_시설의_조건_원문을_파싱한다() {
        // facility.condition.parse.limit이 없으면(facilityConditionParse 태스크) 전량 처리하고,
        // 있으면(facilityConditionParseSample 태스크) 그 건수에서 멈춘다.
        int limit = Integer.getInteger("facility.condition.parse.limit", Integer.MAX_VALUE);
        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseUpTo(limit);

        printResult(result);
    }

    /**
     * NOT_PROCESSED의 약 90%는 조건 원문 자체가 없어 LLM을 호출하지 않고 곧장 NO_CONDITION으로
     * 빠진다. facility_id 순서로 훑는 {@link #NOT_PROCESSED_시설의_조건_원문을_파싱한다}만으로는
     * (특히 조건없음 시설이 앞쪽에 몰려 있으면) 실제 파싱 결과를 한 건도 못 볼 수 있어, 조건
     * 원문이 있는 시설만 골라 LLM 호출을 보장하는 검증 전용 실행기다.
     */
    @Test
    @DisplayName("조건 원문이 있는 NOT_PROCESSED 시설만 골라 파싱한다")
    @EnabledIfSystemProperty(
            named = "facility.condition.parse.withCondition",
            matches = "true",
            disabledReason = "검증 전용 실행기. ./gradlew facilityConditionParseSampleWithCondition 으로 실행한다."
    )
    void 조건_원문이_있는_시설만_골라_파싱한다() {
        int limit = Integer.getInteger("facility.condition.parse.limit", 50);
        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseSampleWithConditionText(limit);

        printResult(result);
    }

    private void printResult(FacilityConditionLlmBatchResult result) {
        System.out.println("========================================");
        System.out.println(" 조건 파싱 결과");
        System.out.println("========================================");
        System.out.println(result.summary());
        System.out.println("========================================");
    }

}
