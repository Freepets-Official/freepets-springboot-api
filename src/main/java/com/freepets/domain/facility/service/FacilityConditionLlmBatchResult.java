package com.freepets.domain.facility.service;

import java.util.EnumMap;
import java.util.Map;

import com.freepets.domain.facility.entity.PetConditionStatus;

import lombok.Getter;

/** {@code FacilityConditionLlmBatchService} 배치 결과 집계. */
@Getter
public class FacilityConditionLlmBatchResult {

    private int processed;
    private int failed;

    private final Map<PetConditionStatus, Integer> statusCounts = new EnumMap<>(PetConditionStatus.class);

    public void add(PetConditionStatus status) {
        processed++;
        statusCounts.merge(status, 1, Integer::sum);
    }

    /** 개별 시설 파싱이 실패해도(LLM 호출 오류 등) 배치 전체를 멈추지 않고 건너뛴 건수. */
    public void addFailed() {
        failed++;
    }

    public int countOf(PetConditionStatus status) {
        return statusCounts.getOrDefault(status, 0);
    }

    public String summary() {
        return "처리 %d, 실패(건너뜀) %d | 조건없음 %d, 파싱완료 %d, 애매함 %d"
                .formatted(
                        processed,
                        failed,
                        countOf(PetConditionStatus.NO_CONDITION),
                        countOf(PetConditionStatus.PARSED),
                        countOf(PetConditionStatus.AMBIGUOUS)
                );
    }

}
