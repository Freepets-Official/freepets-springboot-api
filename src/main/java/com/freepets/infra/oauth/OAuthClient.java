package com.freepets.infra.oauth;

import com.freepets.domain.user.entity.Provider;

/**
 * 제공자별 소셜 토큰 검증기.
 *
 * <p>앱이 각 소셜 SDK로 로그인해 받은 토큰을 그대로 넘겨받아 검증한다.
 * 카카오·네이버는 액세스 토큰으로 userinfo API를 호출하고,
 * 구글·애플은 id_token 서명을 JWKS로 검증한다.
 */
public interface OAuthClient {

    Provider getProvider();

    /**
     * @param providerToken  카카오·네이버는 액세스 토큰, 구글·애플은 id_token
     * @param nameFromClient 애플 최초 로그인에서만 의미가 있다. 애플은 이름을 id_token에 담지 않고
     *                       최초 인가 응답에서 클라이언트에게 한 번만 주기 때문이다. 그 외 제공자는 무시한다
     * @throws OAuthException 토큰이 유효하지 않거나 제공자와 통신하지 못한 경우
     */
    OAuthUserInfo fetchUserInfo(
            String providerToken,
            String nameFromClient
    );
}
