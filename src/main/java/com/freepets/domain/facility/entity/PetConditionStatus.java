package com.freepets.domain.facility.entity;

/**
 * 시설 조건 원문의 파싱 진행 상태.
 *
 * <p>{@code PetAllowed}(동반 가능 여부)와는 다른 축이다 — 이건 "조건 원문을 얼마나
 * 구조화했는지"를 나타낸다. {@link com.freepets.domain.facility.service.FacilityConditionLlmParser}가 채운다.
 */
public enum PetConditionStatus {

    /** 아직 파싱을 시도하지 않음(적재 직후 기본값). */
    NOT_PROCESSED,

    /** 조건 원문 자체가 비어 있음이 확인됨 — LLM 호출 없이 결정(실측 데이터 기준 약 90%). */
    NO_CONDITION,

    /** LLM이 원문을 전부 컬럼에 담아냄 — 잔여 텍스트(unmappedConditionText) 없음. */
    PARSED,

    /**
     * 컬럼으로 못 담는 조건 문장이 남음. LLM이 "애매한지"를 주관적으로 판단하는 게 아니라,
     * {@code unmappedConditionText}가 비어있지 않으면 기계적으로 이 상태가 된다.
     */
    AMBIGUOUS

}
