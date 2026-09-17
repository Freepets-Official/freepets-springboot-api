package com.freepets.domain.gamification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Badge#getFamily()/getTier()가 상수 이름({패밀리}_{단계})을 파싱해서 유도하는 방식이라,
// 이름 규칙이 깨지면 조용히 IllegalArgumentException을 던진다 — 42개 상수 전부가 규칙을
// 지키는지, 그리고 파생된 family의 relatedSourceType이 badge 자신의 것과 어긋나지 않는지
// 여기서 한 번에 확인한다.
class BadgeFamilyTierNamingTest {

    @Test
    void 모든_배지는_이름_규칙에서_패밀리와_단계를_유도할_수_있다() {
        for (Badge badge : Badge.values()) {
            BadgeFamily family = badge.getFamily();
            BadgeTier tier = badge.getTier();

            assertThat(family.name() + "_" + tier.name())
                    .as("%s의 이름이 {패밀리}_{단계} 규칙을 따라야 함", badge)
                    .isEqualTo(badge.name());
        }
    }

    @Test
    void 배지의_relatedSourceType은_같은_패밀리의_relatedSourceType과_같다() {
        for (Badge badge : Badge.values()) {
            assertThat(badge.getRelatedSourceType())
                    .as("%s의 relatedSourceType이 소속 패밀리(%s)와 어긋남", badge, badge.getFamily())
                    .isEqualTo(badge.getFamily().getRelatedSourceType());
        }
    }

    @Test
    void 패밀리마다_6단계가_전부_한_번씩만_존재한다() {
        for (BadgeFamily family : BadgeFamily.values()) {
            long count = java.util.Arrays.stream(Badge.values())
                    .filter(badge -> badge.getFamily() == family)
                    .count();
            assertThat(count).as("패밀리 %s가 정확히 6단계를 가져야 함", family).isEqualTo(6);
        }
    }
}
