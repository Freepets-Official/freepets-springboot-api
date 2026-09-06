package com.freepets.infra.oauth;

import org.springframework.security.oauth2.jwt.Jwt;

import com.freepets.domain.user.entity.Provider;

/**
 * 애플 id_token을 검증해 사용자 정보를 꺼낸다.
 *
 * <p><b>애플은 이름을 id_token에 담지 않는다.</b> 사용자 이름은 최초 인가 응답에서
 * 클라이언트에게 단 한 번만 전달되므로, 앱이 최초 로그인 때 {@code nameFromClient}로
 * 함께 보내줘야 한다. 두 번째 로그인부터는 이미 DB에 저장돼 있어 필요 없다.
 *
 * <p>{@code sub}는 Apple Developer Team 단위로 고유하며 사용자가 비공개 릴레이 이메일을
 * 바꾸거나 전달을 중단해도 변하지 않는다. 그래서 이메일이 아니라 이 값으로 사용자를 찾는다.
 *
 * <p>{@code email}은 비공개 릴레이 주소({@code ...@privaterelay.appleid.com})일 수 있고,
 * 사용자가 이메일 공유에 동의하지 않으면 아예 없을 수도 있다.
 */
public class AppleOAuthClient implements OAuthClient {

    private final OidcIdTokenVerifier idTokenVerifier;

    public AppleOAuthClient(OidcIdTokenVerifier idTokenVerifier) {
        this.idTokenVerifier = idTokenVerifier;
    }

    @Override
    public Provider getProvider() {
        return Provider.APPLE;
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
                nameFromClient
        );
    }
}
