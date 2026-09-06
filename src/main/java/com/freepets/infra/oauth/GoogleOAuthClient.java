package com.freepets.infra.oauth;

import org.springframework.security.oauth2.jwt.Jwt;

import com.freepets.domain.user.entity.Provider;

/**
 * 구글 id_token을 검증해 사용자 정보를 꺼낸다.
 *
 * <p>구글은 백엔드 연동 시 액세스 토큰이 아니라 id_token을 서버로 보내 검증하도록 안내한다.
 * id_token은 {@code aud}에 클라이언트 ID가 서명과 함께 박혀 있어, 다른 앱에서 발급된 토큰을
 * 걸러낼 수 있다(카카오·네이버 액세스 토큰에는 없는 성질이다).
 *
 * <p>{@code sub}는 구글 계정 고유 식별자이고 이메일을 바꿔도 변하지 않는다.
 */
public class GoogleOAuthClient implements OAuthClient {

    private final OidcIdTokenVerifier idTokenVerifier;

    public GoogleOAuthClient(OidcIdTokenVerifier idTokenVerifier) {
        this.idTokenVerifier = idTokenVerifier;
    }

    @Override
    public Provider getProvider() {
        return Provider.GOOGLE;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(
            String providerToken,
            String nameFromClient
    ) {
        Jwt idToken = idTokenVerifier.verify(providerToken);

        return new OAuthUserInfo(
                idToken.getSubject(),
                idToken.getClaimAsString("email"),
                idToken.getClaimAsString("name")
        );
    }
}
