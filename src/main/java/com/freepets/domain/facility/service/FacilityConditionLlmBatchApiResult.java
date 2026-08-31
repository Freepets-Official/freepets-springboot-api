package com.freepets.domain.facility.service;

import java.util.EnumMap;
import java.util.Map;

import com.freepets.domain.facility.entity.PetConditionStatus;

import lombok.Getter;

/** {@code FacilityConditionLlmBatchApiService} 배치 결과 집계. */
@Getter
public class FacilityConditionLlmBatchApiResult {

    private int submitted;
    private int applied;
    private int failed;

    private final Map<PetConditionStatus, Integer> statusCounts = new EnumMap<>(PetConditionStatus.class);

    public void addSubmitted(int count) {
        submitted += count;
    }

    public void addApplied(PetConditionStatus status) {
        applied++;
        statusCounts.merge(status, 1, Integer::sum);
    }

    /** 개별 결과가 errored/canceled/expired이거나 적용 중 오류가 나면 건너뛴 건수. */
    public void addFailed() {
        failed++;
    }

    public int countOf(PetConditionStatus status) {
        return statusCounts.getOrDefault(status, 0);
    }

    public String summary() {
        return "제출 %d, 적용 %d, 실패(건너뜀) %d | 조건없음 %d, 파싱완료 %d, 애매함 %d"
                .formatted(
                        submitted,
                        applied,
                        failed,
                        countOf(PetConditionStatus.NO_CONDITION),
                        countOf(PetConditionStatus.PARSED),
                        countOf(PetConditionStatus.AMBIGUOUS)
                );
    }

}
