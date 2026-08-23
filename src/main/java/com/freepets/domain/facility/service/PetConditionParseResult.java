package com.freepets.domain.facility.service;

import java.math.BigDecimal;
import java.util.List;

import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

/**
 * 조건 원문 파싱 결과.
 *
 * @param petAllowed   동반 가능 여부. 파싱 대상은 등재 시설뿐이므로 {@code ALLOWED} 또는 {@code DENIED}다
 * @param maxWeight    체중 상한(kg). 원문에 명시되지 않으면 {@code null}
 * @param requirements 출입 요구조건. 명시된 것이 없으면 빈 목록
 */
public record PetConditionParseResult(
        PetAllowed petAllowed,
        BigDecimal maxWeight,
        List<Requirement> requirements
) {
}
