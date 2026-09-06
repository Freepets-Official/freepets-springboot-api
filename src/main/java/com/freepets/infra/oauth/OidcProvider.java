package com.freepets.infra.oauth;

import java.util.Set;

/**
 * id_token을 발급하는 제공자(구글·애플)의 검증 엔드포인트와 허용 issuer.
 *
 * <p>카카오·네이버는 액세스 토큰으로 userinfo API를 호출하는 방식이라 여기 없다.
 */
enum OidcProvider {

    /**
     * 구글은 {@code iss}를 스킴이 있는 형태와 없는 형태 <b>두 가지로</b> 발급한다.
     * 하나만 허용하면 멀쩡한 토큰이 간헐적으로 거부되므로 둘 다 받는다.
     */
    GOOGLE(
            "https://www.googleapis.com/oauth2/v3/certs",
            Set.of("https://accounts.google.com", "accounts.google.com")
    ),

    APPLE(
            "https://appleid.apple.com/auth/keys",
            Set.of("https://appleid.apple.com")
    );

    private final String jwkSetUri;
    private final Set<String> allowedIssuers;

    OidcProvider(
            String jwkSetUri,
            Set<String> allowedIssuers
    ) {
        this.jwkSetUri = jwkSetUri;
        this.allowedIssuers = allowedIssuers;
    }

    String getJwkSetUri() {
        return jwkSetUri;
    }

    Set<String> getAllowedIssuers() {
        return allowedIssuers;
    }
}
