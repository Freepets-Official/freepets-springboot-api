package com.freepets.domain.gamification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class LevelTierTest {

    @Test
    void 레벨_1은_빨강_80퍼센트다() {
        LevelTier tier = LevelTier.of(1);

        assertThat(tier.color()).isEqualTo(PawColor.RED);
        assertThat(tier.opacityPercent()).isEqualTo(80);
    }

    @Test
    void 같은_색_안에서_레벨마다_투명도가_20퍼센트씩_옅어지다가_다섯_레벨을_다_채우면_다음_색으로_넘어간다() {
        assertThat(LevelTier.of(1).opacityPercent()).isEqualTo(80);
        assertThat(LevelTier.of(2).opacityPercent()).isEqualTo(60);
        assertThat(LevelTier.of(3).opacityPercent()).isEqualTo(40);
        assertThat(LevelTier.of(4).opacityPercent()).isEqualTo(20);
        assertThat(LevelTier.of(5).opacityPercent()).isEqualTo(0);
        assertThat(LevelTier.of(5).color()).isEqualTo(PawColor.RED);

        // 6레벨부터 다음 색(주황)으로 넘어가면서 투명도는 다시 80%부터.
        assertThat(LevelTier.of(6).color()).isEqualTo(PawColor.ORANGE);
        assertThat(LevelTier.of(6).opacityPercent()).isEqualTo(80);
    }

    @Test
    void 일곱_색을_다_돌면_무지개_슬롯으로_넘어간다() {
        // 31~35레벨은 마지막 무지개 이전 색(보라).
        assertThat(LevelTier.of(31).color()).isEqualTo(PawColor.VIOLET);
        assertThat(LevelTier.of(31).opacityPercent()).isEqualTo(80);
        assertThat(LevelTier.of(35).color()).isEqualTo(PawColor.VIOLET);
        assertThat(LevelTier.of(35).opacityPercent()).isEqualTo(0);

        // 36레벨부터 무지개로 넘어가면서 투명도가 다시 처음부터.
        assertThat(LevelTier.of(36).color()).isEqualTo(PawColor.RAINBOW);
        assertThat(LevelTier.of(36).opacityPercent()).isEqualTo(80);
    }

    @Test
    void 레벨_40이_무지개의_마지막_투명도다() {
        // 8슬롯(7색+무지개) × 5단계 = 40 — 레벨 상한(LevelCurve.MAX_LEVEL)과 정확히 맞아떨어진다.
        LevelTier tier = LevelTier.of(40);

        assertThat(tier.color()).isEqualTo(PawColor.RAINBOW);
        assertThat(tier.opacityPercent()).isEqualTo(0);
    }

    @Test
    void 레벨_40을_넘으면_40으로_클램핑한다() {
        LevelTier tier = LevelTier.of(999);

        assertThat(tier.level()).isEqualTo(999);
        assertThat(tier.color()).isEqualTo(PawColor.RAINBOW);
        assertThat(tier.opacityPercent()).isEqualTo(0);
    }

    @Test
    void 여덟_색_슬롯이_정확히_다섯_레벨씩_차지한다() {
        for (PawColor color : PawColor.values()) {
            long count = IntStream.rangeClosed(1, 40)
                    .mapToObj(LevelTier::of)
                    .filter(tier -> tier.color() == color)
                    .count();
            assertThat(count).as("색 %s가 정확히 5개 레벨을 차지해야 함", color).isEqualTo(5);
        }
    }

    @Test
    void 레벨_1_미만이면_레벨_1로_취급한다() {
        LevelTier tier = LevelTier.of(0);

        assertThat(tier.level()).isEqualTo(1);
        assertThat(tier.color()).isEqualTo(PawColor.RED);
        assertThat(tier.opacityPercent()).isEqualTo(80);
    }

    @Test
    void 라벨은_색과_투명도를_조합한_문자열이다() {
        LevelTier tier = LevelTier.of(6);

        assertThat(tier.label()).isEqualTo("주황 80% 발바닥");
    }
}
