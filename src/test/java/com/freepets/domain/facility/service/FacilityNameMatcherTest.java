package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FacilityNameMatcherTest {

    @Test
    void 완전히_같은_이름은_비슷하다고_본다() {
        assertThat(FacilityNameMatcher.isSimilar("카페 파도살롱", "카페 파도살롱")).isTrue();
    }

    @Test
    void 공백과_대소문자_차이는_무시한다() {
        assertThat(FacilityNameMatcher.isSimilar("Cafe Wave", "cafe   wave")).isTrue();
    }

    @Test
    void 한쪽이_다른쪽을_포함하면_비슷하다고_본다() {
        assertThat(FacilityNameMatcher.isSimilar("카페 파도살롱", "카페 파도살롱 본점")).isTrue();
        assertThat(FacilityNameMatcher.isSimilar("카페 파도살롱 본점", "카페 파도살롱")).isTrue();
    }

    @Test
    void 사소한_오탈자는_비슷하다고_본다() {
        // 편집거리 1(음절 하나 차이)
        assertThat(FacilityNameMatcher.isSimilar("카페 파도살롱", "카페 파도살룽")).isTrue();
    }

    @Test
    void 명백히_다른_이름은_비슷하지_않다고_본다() {
        assertThat(FacilityNameMatcher.isSimilar("카페 파도살롱", "김밥천국")).isFalse();
    }

    @Test
    void null이면_비슷하지_않다고_본다() {
        assertThat(FacilityNameMatcher.isSimilar(null, "카페 파도살롱")).isFalse();
        assertThat(FacilityNameMatcher.isSimilar("카페 파도살롱", null)).isFalse();
    }
}
