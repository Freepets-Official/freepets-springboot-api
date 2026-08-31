package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code petConditionStatus = NOT_PROCESSED}인 시설을 Anthropic Batch API로 파싱하는 실행기.
 *
 * <p>실제로 LLM을 호출하고(배치 하나로 묶어서), 처리가 끝날 때까지(수 분~수 시간) 대기한다 —
 * {@code test} 태스크에서는 실행되지 않는다. 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilityConditionParseBatch
 * ./gradlew facilityConditionParseBatchSample   (소규모 검증용, 기본 10건)
 * </pre>
 *
 * <p>인증키는 {@code application.yml}의 {@code anthropic.api-key}에서 읽는다.
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "facility.condition.parse.batch",
        matches = "true",
        disabledReason = "Batch API 조건 파싱 전용 실행기. ./gradlew facilityConditionParseBatch 로 실행한다."
)
class FacilityConditionLlmBatchApiRunner {

    @Autowired
    private FacilityConditionLlmBatchApiService facilityConditionLlmBatchApiService;

    @Test
    @DisplayName("NOT_PROCESSED 시설을 Batch API로 파싱한다")
    void NOT_PROCESSED_시설을_Batch_API로_파싱한다() {
        // facility.condition.parse.batch.limit이 없으면(facilityConditionParseBatch 태스크)
        // 전량 처리하고, 있으면(facilityConditionParseBatchSample 태스크) 그 건수만 제출한다.
        int limit = Integer.getInteger("facility.condition.parse.batch.limit", Integer.MAX_VALUE);
        FacilityConditionLlmBatchApiResult result = facilityConditionLlmBatchApiService.run(limit);

        System.out.println("========================================");
        System.out.println(" Batch API 조건 파싱 결과");
        System.out.println("========================================");
        System.out.println(result.summary());
        System.out.println("========================================");
    }

}
