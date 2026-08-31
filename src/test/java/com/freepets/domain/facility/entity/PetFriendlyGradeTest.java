package com.freepets.domain.facility.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

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

    @ParameterizedTest
    @CsvSource({
            "최고 등급, 5",
            "동반 우수, 4",
            "동반 추천, 3",
            "동반 편안, 2",
            "동반 가능, 1"
    })
    @DisplayName("등급마다 발자국 개수가 매겨져 있다")
    void 등급마다_발자국_개수가_매겨져_있다(
            String label,
            int expectedLevel
    ) {
        PetFriendlyGrade grade = Arrays.stream(PetFriendlyGrade.values())
                .filter(candidate -> candidate.getLabel().equals(label))
                .findFirst()
                .orElseThrow();

        assertThat(grade.getLevel()).isEqualTo(expectedLevel);
    }

    @ParameterizedTest
    @CsvSource({
            "87.96, 90, 3",
            "88.0, 90, 4",
            "93.99, 150, 4",
            "94.0, 150, 5"
    })
    @DisplayName("소수 점수는 반올림 없이 그대로 기준과 비교한다")
    void 소수_점수는_반올림_없이_그대로_기준과_비교한다(
            double petScore,
            long reviewCount,
            int expectedLevel
    ) {
        assertThat(PetFriendlyGrade.levelOf(PetFriendlyGrade.ofScore(petScore, reviewCount)))
                .isEqualTo(expectedLevel);
    }

    @Test
    @DisplayName("등급이 없으면 레벨은 0이다")
    void 등급이_없으면_레벨은_0이다() {
        assertThat(PetFriendlyGrade.levelOf(null)).isEqualTo(PetFriendlyGrade.NO_GRADE_LEVEL);
        assertThat(PetFriendlyGrade.levelOf(PetFriendlyGrade.ofScore(100, 9))).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 리뷰 수집 중 (0/10)",
            "3, 리뷰 수집 중 (3/10)"
    })
    @DisplayName("등급이 없으면 얼마나 모였는지 안내 문구로 알린다")
    void 등급이_없으면_얼마나_모였는지_안내_문구로_알린다(
            long reviewCount,
            String expectedLabel
    ) {
        assertThat(PetFriendlyGrade.collectingLabel(reviewCount)).isEqualTo(expectedLabel);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 10",
            "3, 7",
            "10, 0",
            "15, 0"
    })
    @DisplayName("첫 등급까지 남은 리뷰 수는 음수가 되지 않는다")
    void 첫_등급까지_남은_리뷰_수는_음수가_되지_않는다(
            long reviewCount,
            long expectedNeedMore
    ) {
        assertThat(PetFriendlyGrade.needMore(reviewCount)).isEqualTo(expectedNeedMore);
    }
}
