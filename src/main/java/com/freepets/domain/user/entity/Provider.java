package com.freepets.domain.user.entity;

/**
 * 계정이 어떤 경로로 만들어졌는지 나타낸다.
 *
 * <p>{@link #LOCAL}은 이메일/비밀번호 가입이고 나머지는 소셜 로그인이다.
 * 소셜 계정은 이메일이 아니라 {@code (provider, providerId)} 조합으로 식별한다.
 */
public enum Provider {
    KAKAO,
    NAVER,
    GOOGLE,
    APPLE,
    LOCAL;

    public boolean isSocial() {
        return this != LOCAL;
    }
}
