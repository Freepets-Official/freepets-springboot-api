package com.freepets.domain.pet.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DangerousDogBreedTest {

    @Test
    void 법정_맹견_품종명이면_true() {
        assertThat(DangerousDogBreed.matches("도사견")).isTrue();
        assertThat(DangerousDogBreed.matches("아메리칸 핏불 테리어")).isTrue();
        assertThat(DangerousDogBreed.matches("아메리칸 스태퍼드셔 테리어")).isTrue();
        assertThat(DangerousDogBreed.matches("스태퍼드셔 불 테리어")).isTrue();
        assertThat(DangerousDogBreed.matches("로트와일러")).isTrue();
    }

    @Test
    void 잡종이어도_품종명이_포함되면_true() {
        assertThat(DangerousDogBreed.matches("로트와일러 믹스")).isTrue();
        assertThat(DangerousDogBreed.matches("핏불 잡종")).isTrue();
    }

    @Test
    void 영문_품종명도_대소문자_구분없이_true() {
        assertThat(DangerousDogBreed.matches("Rottweiler")).isTrue();
        assertThat(DangerousDogBreed.matches("ROTTWEILER")).isTrue();
        assertThat(DangerousDogBreed.matches("pitbull")).isTrue();
        assertThat(DangerousDogBreed.matches("Pit Bull mix")).isTrue();
    }

    @Test
    void 맹견이_아니면_false() {
        assertThat(DangerousDogBreed.matches("말티즈")).isFalse();
        assertThat(DangerousDogBreed.matches("골든리트리버")).isFalse();
    }

    @Test
    void null이거나_빈_문자열이면_false() {
        assertThat(DangerousDogBreed.matches(null)).isFalse();
        assertThat(DangerousDogBreed.matches("")).isFalse();
        assertThat(DangerousDogBreed.matches("   ")).isFalse();
    }
}
