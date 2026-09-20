package com.freepets.domain.gamification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Badge#family/#tier는 이제 상수 이름 파싱이 아니라 생성자 필드라, 컴파일러가 오타·잘못된
// 참조를 막아준다 — 다만 "패밀리마다 정해진 단계가 빠짐없이 정확히 한 번씩 있는지"는 컴파일러가
// 못 잡는 카탈로그 완결성 문제라 여기서 확인한다(예: 새 패밀리를 추가하며 단계 하나를
// 깜빡 빼먹는 실수). REGION(정복자)만 예외적으로 4단계(BRONZE/SILVER/GOLD/RUBY)만 쓰고
// CRYSTAL·DIAMOND가 없다 — 시/군/구는 시설보다 훨씬 느리게 늘어 공통 6단계 기준으론 다이아가
// 불가능하기 때문(Badge 클래스 주석 참고).
class BadgeCatalogTest {

    private static final Map<BadgeFamily, Set<BadgeTier>> EXPECTED_TIERS_BY_FAMILY = expectedTiersByFamily();

    @Test
    void 패밀리마다_기대되는_단계가_전부_한_번씩만_존재한다() {
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
                    .as("패밀리 %s가 기대되는 단계를 전부 가져야 함", family)
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_TIERS_BY_FAMILY.get(family));
        }
    }

    @Test
    void 배지_개수는_패밀리별_기대_단계_수의_합이다() {
        int expectedTotal = EXPECTED_TIERS_BY_FAMILY.values().stream().mapToInt(Set::size).sum();
        assertThat(Badge.values()).hasSize(expectedTotal);
    }

    private static Map<BadgeFamily, Set<BadgeTier>> expectedTiersByFamily() {
        Map<BadgeFamily, Set<BadgeTier>> expected = new EnumMap<>(BadgeFamily.class);
        for (BadgeFamily family : BadgeFamily.values()) {
            expected.put(family, family == BadgeFamily.REGION
                    ? EnumSet.of(BadgeTier.BRONZE, BadgeTier.SILVER, BadgeTier.GOLD, BadgeTier.RUBY)
                    : EnumSet.allOf(BadgeTier.class));
        }
        return expected;
    }

}
