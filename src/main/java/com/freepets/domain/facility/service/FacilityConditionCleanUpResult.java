package com.freepets.domain.facility.service;

import lombok.Getter;

/** {@code FacilityConditionLlmBatchService.cleanUpMaxWeightWithoutSourceEvidence} 결과 집계. */
@Getter
public class FacilityConditionCleanUpResult {

    private int checked;
    private int cleaned;

    public void addChecked() {
        checked++;
    }

    public void addCleaned() {
        cleaned++;
    }

    public String summary() {
        return "검사 %d, 정리 %d".formatted(checked, cleaned);
    }

}
