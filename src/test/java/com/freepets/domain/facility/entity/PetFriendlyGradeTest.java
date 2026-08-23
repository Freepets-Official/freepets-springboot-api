package com.freepets.domain.facility.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 등급 판정 검증. 기준값은 기능명세서 F6-3의 발자국 등급표를 따른다.
 */
class PetFriendlyGradeTest {

    @ParameterizedTest
    @CsvSource({
            "94, 150, 최고 등급",
            "88, 90, 동반 우수",
            "80, 50, 동반 추천",
            "70, 25, 동반 편안",
            "60, 10, 동반 가능"
    })
    @DisplayName("점수와 리뷰 수가 기준을 정확히 만족하면 해당 등급이 된다")
    void 점수와_리뷰_수가_기준을_정확히_만족하면_해당_등급이_된다(
            int petScore,
            long reviewCount,
            String expectedLabel
    ) {
        assertThat(PetFriendlyGrade.labelOf(petScore, reviewCount)).isEqualTo(expectedLabel);
    }

    @Test
    @DisplayName("점수가 한 단계 모자라면 아래 등급으로 내려간다")
    void 점수가_한_단계_모자라면_아래_등급으로_내려간다() {
        assertThat(PetFriendlyGrade.labelOf(93, 150)).isEqualTo("동반 우수");
    }

    @Test
    @DisplayName("점수가 충분해도 리뷰 수가 모자라면 아래 등급으로 내려간다")
    void 점수가_충분해도_리뷰_수가_모자라면_아래_등급으로_내려간다() {
        assertThat(PetFriendlyGrade.labelOf(94, 149)).isEqualTo("동반 우수");
    }

    @ParameterizedTest
    @CsvSource({
            "59, 10",
            "60, 9",
            "100, 5"
    })
    @DisplayName("가장 낮은 등급에도 못 미치면 등급을 주지 않는다")
    void 가장_낮은_등급에도_못_미치면_등급을_주지_않는다(
            int petScore,
            long reviewCount
    ) {
        assertThat(PetFriendlyGrade.of(petScore, reviewCount)).isNull();
        assertThat(PetFriendlyGrade.labelOf(petScore, reviewCount)).isNull();
    }

    @Test
    @DisplayName("친화도 점수가 아직 없으면 등급을 주지 않는다")
    void 친화도_점수가_아직_없으면_등급을_주지_않는다() {
        assertThat(PetFriendlyGrade.labelOf(null, 1000)).isNull();
    }
}
