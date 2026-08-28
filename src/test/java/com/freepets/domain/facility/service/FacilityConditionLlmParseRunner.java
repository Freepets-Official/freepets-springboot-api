package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    void NOT_PROCESSED_시설의_조건_원문을_파싱한다() {
        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseAll();

        System.out.println("========================================");
        System.out.println(" 조건 파싱 결과");
        System.out.println("========================================");
        System.out.println(result.summary());
        System.out.println("========================================");
    }

}
