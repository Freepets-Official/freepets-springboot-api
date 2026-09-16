package com.freepets.domain.gamification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelTierTest {

    @Test
    void 레벨_1은_개_흐릿함_빨강이다() {
        LevelTier tier = LevelTier.of(1);

        assertThat(tier.animal()).isEqualTo(PawAnimal.DOG);
        assertThat(tier.finish()).isEqualTo(PawFinish.DIM);
        assertThat(tier.color()).isEqualTo(PawColor.RED);
    }

    @Test
    void 레벨마다_한_칸씩_색이_바뀌다가_7색을_다_돌면_선명도_단계가_바뀐다() {
        // 1~7 레벨은 전부 흐릿함(DIM) 단계로 무지개 순서를 그대로 따라간다.
        assertThat(LevelTier.of(1).color()).isEqualTo(PawColor.RED);
        assertThat(LevelTier.of(2).color()).isEqualTo(PawColor.ORANGE);
        assertThat(LevelTier.of(3).color()).isEqualTo(PawColor.YELLOW);
        assertThat(LevelTier.of(4).color()).isEqualTo(PawColor.GREEN);
        assertThat(LevelTier.of(5).color()).isEqualTo(PawColor.BLUE);
        assertThat(LevelTier.of(6).color()).isEqualTo(PawColor.INDIGO);
        assertThat(LevelTier.of(7).color()).isEqualTo(PawColor.VIOLET);
        assertThat(LevelTier.of(7).finish()).isEqualTo(PawFinish.DIM);

        // 8레벨부터 다음 선명도 단계(또렷함)로 넘어가면서 색은 다시 빨강부터.
        assertThat(LevelTier.of(8).finish()).isEqualTo(PawFinish.CLEAR);
        assertThat(LevelTier.of(8).color()).isEqualTo(PawColor.RED);
        assertThat(LevelTier.of(8).animal()).isEqualTo(PawAnimal.DOG);
    }

    @Test
    void 다섯_단계를_다_돌면_동물이_바뀐다() {
        // 35레벨까지는 개(DOG) — 5단계(DIM~HOLOGRAPHIC) × 7색을 다 채운다.
        assertThat(LevelTier.of(35).animal()).isEqualTo(PawAnimal.DOG);
        assertThat(LevelTier.of(35).finish()).isEqualTo(PawFinish.HOLOGRAPHIC);
        assertThat(LevelTier.of(35).color()).isEqualTo(PawColor.VIOLET);

        // 36레벨부터 고양이로 넘어가면서 단계·색이 다시 처음부터.
        assertThat(LevelTier.of(36).animal()).isEqualTo(PawAnimal.CAT);
        assertThat(LevelTier.of(36).finish()).isEqualTo(PawFinish.DIM);
        assertThat(LevelTier.of(36).color()).isEqualTo(PawColor.RED);
    }

    @Test
    void 레벨_70이_두_번째_동물의_마지막_단계_마지막_색이다() {
        // 2종 × 5단계 × 7색 = 70 — 레벨 상한(LevelCurve.MAX_LEVEL)과 정확히 맞아떨어진다.
        LevelTier tier = LevelTier.of(70);

        assertThat(tier.animal()).isEqualTo(PawAnimal.CAT);
        assertThat(tier.finish()).isEqualTo(PawFinish.HOLOGRAPHIC);
        assertThat(tier.color()).isEqualTo(PawColor.VIOLET);
    }

    @Test
    void 두_동물이_정확히_35개_레벨씩_차지한다() {
        for (PawAnimal animal : PawAnimal.values()) {
            long count = java.util.stream.IntStream.rangeClosed(1, 70)
                    .mapToObj(LevelTier::of)
                    .filter(tier -> tier.animal() == animal)
                    .count();
            assertThat(count).as("동물 %s가 정확히 35개 레벨을 차지해야 함", animal).isEqualTo(35);
        }
    }

    @Test
    void 다섯_단계가_동물마다_정확히_7개_레벨씩_차지한다() {
        for (PawAnimal animal : PawAnimal.values()) {
            for (PawFinish finish : PawFinish.values()) {
                long count = java.util.stream.IntStream.rangeClosed(1, 70)
                        .mapToObj(LevelTier::of)
                        .filter(tier -> tier.animal() == animal && tier.finish() == finish)
                        .count();
                assertThat(count)
                        .as("%s의 %s 단계가 정확히 7개 레벨을 차지해야 함", animal, finish)
                        .isEqualTo(7);
            }
        }
    }

    @Test
    void 레벨_1_미만이면_레벨_1로_취급한다() {
        LevelTier tier = LevelTier.of(0);

        assertThat(tier.level()).isEqualTo(1);
        assertThat(tier.animal()).isEqualTo(PawAnimal.DOG);
        assertThat(tier.finish()).isEqualTo(PawFinish.DIM);
        assertThat(tier.color()).isEqualTo(PawColor.RED);
    }

    @Test
    void 라벨은_동물과_단계와_색을_조합한_문자열이다() {
        LevelTier tier = LevelTier.of(8);

        assertThat(tier.label()).isEqualTo("개 발바닥 · 또렷함 · 빨강");
    }
}
