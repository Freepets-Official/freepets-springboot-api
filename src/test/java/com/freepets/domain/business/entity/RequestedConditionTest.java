package com.freepets.domain.business.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

class RequestedConditionTest {

    @Test
    void 같은_요구조건이_여러_번_와도_한_번만_담는다() {
        // 승인 시 이 목록으로 체크리스트를 만들기 때문에, 중복을 두면 같은 조건이 여러 줄로 쌓인다.
        RequestedCondition condition = RequestedCondition.of(
                PetAllowed.ALLOWED,
                null,
                null,
                List.of(Requirement.LEASH, Requirement.MUZZLE, Requirement.LEASH),
                "리드줄과 입마개 착용 시 동반 가능"
        );

        assertThat(condition.getRequirements()).containsExactly(Requirement.LEASH, Requirement.MUZZLE);
    }

    @Test
    void 요구조건을_보내지_않으면_빈_목록이_된다() {
        RequestedCondition condition = RequestedCondition.of(PetAllowed.ALLOWED, null, null, null, null);

        assertThat(condition.getRequirements()).isEmpty();
    }

    @Test
    void 최대_체중이_없으면_경계_종류도_비운다() {
        // 상한이 없으면 "이하"인지 "미만"인지가 의미가 없다(Facility.confirmByOwner와 같은 규칙).
        RequestedCondition condition = RequestedCondition.of(
                PetAllowed.ALLOWED,
                null,
                true,
                List.of(),
                null
        );

        assertThat(condition.getMaxWeight()).isNull();
        assertThat(condition.getMaxWeightInclusive()).isNull();
    }

    @Test
    void 최대_체중이_있으면_경계_종류를_그대로_담는다() {
        RequestedCondition condition = RequestedCondition.of(
                PetAllowed.ALLOWED,
                new BigDecimal("10.00"),
                false,
                List.of(),
                null
        );

        assertThat(condition.getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(condition.getMaxWeightInclusive()).isFalse();
    }
}
