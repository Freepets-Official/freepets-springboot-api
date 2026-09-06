package com.freepets.infra.oauth;

/**
 * 소셜 제공자에서 검증해 얻어낸 사용자 정보.
 *
 * @param providerId 제공자가 발급한 고유 식별자. 카카오 회원번호, 네이버 {@code response.id},
 *                   구글·애플 {@code sub}. 유일하게 반드시 존재하는 값이다
 * @param email      제공자 계정 이메일. 동의를 거부했거나 애플이 감춘 경우 null
 * @param name       사용자 이름. 애플은 id_token에 담지 않아 2회차 로그인부터 null
 */
public record OAuthUserInfo(
        String providerId,
        String email,
        String name
) {

    public OAuthUserInfo {
        if (providerId == null || providerId.isBlank()) {
            throw new OAuthException("소셜 제공자가 고유 식별자를 반환하지 않았습니다.");
        }
    }
}
