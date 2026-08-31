package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 이미 저장된 시설 중 원문에 kg 언급 없이 maxWeight만 채워진 과거 오염 데이터를 찾아
 * 정리하는 일회성 실행기.
 *
 * <p>{@code FacilityConditionLlmBatchService.resolve}의 방어 코드는 그 시설이 배치에서
 * "다시" 처리될 때만 작동하는데, 배치는 {@code petConditionStatus = NOT_PROCESSED}만 훑는다.
 * 이미 PARSED/AMBIGUOUS로 저장된 시설은 원문(관광공사 데이터)이 그대로면 재동기화를 해도
 * 상태가 안 바뀌어({@code Facility#updateFromTourApi} 참고) 배치가 절대 다시 안 건드린다.
 *
 * <p>LLM 호출도 재파싱도 없이 이미 있는 값만 검사해서 지우므로 비용은 안 들지만, 실제로
 * DB에 쓰기가 일어난다. {@code test} 태스크에서는 실행되지 않고 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilityConditionCleanUpMaxWeight
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "facility.condition.cleanUpMaxWeight",
        matches = "true",
        disabledReason = "청소 전용 실행기. ./gradlew facilityConditionCleanUpMaxWeight 로 실행한다."
)
class FacilityConditionCleanUpMaxWeightRunner {

    @Autowired
    private FacilityConditionLlmBatchService facilityConditionLlmBatchService;

    @Test
    @DisplayName("원문에 체중 언급이 없는 maxWeight를 찾아 정리한다")
    void 원문에_체중_언급이_없는_maxWeight를_찾아_정리한다() {
        int limit = Integer.getInteger("facility.condition.cleanUpMaxWeight.limit", Integer.MAX_VALUE);
        FacilityConditionCleanUpResult result = facilityConditionLlmBatchService
                .cleanUpMaxWeightWithoutSourceEvidence(limit);

        System.out.println("========================================");
        System.out.println(" maxWeight 청소 결과");
        System.out.println("========================================");
        System.out.println(result.summary());
        System.out.println("========================================");
    }

}
