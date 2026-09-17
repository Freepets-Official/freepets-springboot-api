package com.freepets.domain.gamification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Badge#family/#tier는 이제 상수 이름 파싱이 아니라 생성자 필드라, 컴파일러가 오타·잘못된
// 참조를 막아준다 — 다만 "패밀리마다 6단계가 빠짐없이 정확히 한 번씩 있는지"는 컴파일러가
// 못 잡는 카탈로그 완결성 문제라 여기서 확인한다(예: 새 패밀리를 추가하며 단계 하나를
// 깜빡 빼먹는 실수).
class BadgeCatalogTest {

    @Test
    void 패밀리마다_6단계가_전부_한_번씩만_존재한다() {
        for (BadgeFamily family : BadgeFamily.values()) {
            Set<BadgeTier> tiersSeen = EnumSet.noneOf(BadgeTier.class);
            for (Badge badge : Badge.values()) {
                if (badge.getFamily() != family) {
                    continue;
                }
                assertThat(tiersSeen.add(badge.getTier()))
                        .as("%s에 %s 단계가 중복됨", family, badge.getTier())
                        .isTrue();
            }
            assertThat(tiersSeen)
                    .as("패밀리 %s가 6단계를 전부 가져야 함", family)
                    .containsExactlyInAnyOrder(BadgeTier.values());
        }
    }

    @Test
    void 배지_개수는_패밀리_수_곱하기_6이다() {
        assertThat(Badge.values()).hasSize(BadgeFamily.values().length * BadgeTier.values().length);
    }
}
