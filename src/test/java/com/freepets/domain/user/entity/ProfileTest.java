package com.freepets.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProfileTest {

    @Test
    void of_소유_매장이_없으면_소비자_프로필만_반환한다() {
        assertThat(Profile.of(List.of())).containsExactly(Profile.CONSUMER);
    }

    @Test
    void of_소유_매장이_있으면_사업자_프로필을_함께_반환한다() {
        assertThat(Profile.of(List.of(6L, 9L))).containsExactly(Profile.CONSUMER, Profile.OWNER);
    }
}
