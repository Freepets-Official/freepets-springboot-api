package com.freepets.domain.gamification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelTierTest {

    @Test
    void 레벨_1은_개_빨강이다() {
        LevelTier tier = LevelTier.of(1);

        assertThat(tier.animal()).isEqualTo(PawAnimal.DOG);
        assertThat(tier.color()).isEqualTo(PawColor.RED);
    }

    @Test
    void 레벨마다_한_칸씩_색이_바뀌다가_7색을_다_돌면_동물이_바뀐다() {
        // 1~7 레벨은 전부 개(DOG)로 무지개 순서를 그대로 따라간다.
        assertThat(LevelTier.of(1).color()).isEqualTo(PawColor.RED);
        assertThat(LevelTier.of(2).color()).isEqualTo(PawColor.ORANGE);
        assertThat(LevelTier.of(3).color()).isEqualTo(PawColor.YELLOW);
        assertThat(LevelTier.of(4).color()).isEqualTo(PawColor.GREEN);
        assertThat(LevelTier.of(5).color()).isEqualTo(PawColor.BLUE);
        assertThat(LevelTier.of(6).color()).isEqualTo(PawColor.INDIGO);
        assertThat(LevelTier.of(7).color()).isEqualTo(PawColor.VIOLET);
        assertThat(LevelTier.of(7).animal()).isEqualTo(PawAnimal.DOG);

        // 8레벨부터 다음 동물(고양이)로 넘어가면서 색은 다시 빨강부터.
        assertThat(LevelTier.of(8).animal()).isEqualTo(PawAnimal.CAT);
        assertThat(LevelTier.of(8).color()).isEqualTo(PawColor.RED);
    }

    @Test
    void 열_번째_동물의_마지막_색이_레벨_70이다() {
        // 10종 × 7색 = 70 — 레벨 상한(LevelCurve.MAX_LEVEL)과 정확히 맞아떨어진다.
        LevelTier tier = LevelTier.of(70);

        assertThat(tier.animal()).isEqualTo(PawAnimal.LIZARD);
        assertThat(tier.color()).isEqualTo(PawColor.VIOLET);
    }

    @Test
    void 열_마리_동물이_전부_한_번씩만_쓰인다() {
        // 각 동물은 정확히 7개 레벨(그 동물의 7색)에서만 나타나야 한다.
        for (PawAnimal animal : PawAnimal.values()) {
            long count = java.util.stream.IntStream.rangeClosed(1, 70)
                    .mapToObj(LevelTier::of)
                    .filter(tier -> tier.animal() == animal)
                    .count();
            assertThat(count).as("동물 %s가 정확히 7개 레벨을 차지해야 함", animal).isEqualTo(7);
        }
    }

    @Test
    void 레벨_1_미만이면_레벨_1로_취급한다() {
        LevelTier tier = LevelTier.of(0);

        assertThat(tier.level()).isEqualTo(1);
        assertThat(tier.animal()).isEqualTo(PawAnimal.DOG);
        assertThat(tier.color()).isEqualTo(PawColor.RED);
    }

    @Test
    void 라벨은_동물과_색을_조합한_문자열이다() {
        LevelTier tier = LevelTier.of(8);

        assertThat(tier.label()).isEqualTo("고양이 발바닥 · 빨강");
    }
}
