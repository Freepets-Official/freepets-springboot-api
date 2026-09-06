package com.freepets.infra.oauth;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.freepets.domain.user.entity.Provider;

/**
 * 실제 소셜 토큰으로 검증 경로를 확인하는 일회성 실행기. 외부 API를 호출하므로 {@code test}
 * 태스크에서는 돌지 않는다({@code TourApiProbeTest}와 같은 방식).
 *
 * <pre>
 * ./gradlew oauthProbe -Doauth.probe.provider=kakao  -Doauth.probe.token=&lt;액세스 토큰&gt;
 * ./gradlew oauthProbe -Doauth.probe.provider=naver  -Doauth.probe.token=&lt;액세스 토큰&gt;
 * ./gradlew oauthProbe -Doauth.probe.provider=google -Doauth.probe.token=&lt;id_token&gt; \
 *                      -Doauth.probe.client-ids=&lt;클라이언트 ID&gt;
 * ./gradlew oauthProbe -Doauth.probe.provider=apple  -Doauth.probe.token=&lt;id_token&gt; \
 *                      -Doauth.probe.client-ids=&lt;Bundle ID&gt;
 * </pre>
 *
 * <p>구글·애플은 {@code aud} 검증에 클라이언트 ID가 필요하다. 콤마로 여러 개를 줄 수 있다.
 * 카카오는 {@code -Doauth.probe.kakao-app-id=&lt;앱 ID&gt;}를 주면 토큰 발급 앱 대조까지 확인한다.
 */
@EnabledIfSystemProperty(named = "oauth.probe", matches = "true")
class OAuthProbeTest {

    @Test
    void 실제_소셜_토큰으로_사용자_정보를_조회한다() {
        String providerName = requireProperty("oauth.probe.provider");
        String providerToken = requireProperty("oauth.probe.token");
        String nameFromClient = System.getProperty("oauth.probe.name");

        Provider provider = Provider.valueOf(providerName.trim().toUpperCase());
        OAuthClient client = createClient(provider);

        OAuthUserInfo userInfo = client.fetchUserInfo(providerToken, nameFromClient);

        System.out.println("=== " + provider + " ===");
        System.out.println("providerId = " + userInfo.providerId());
        System.out.println("email      = " + userInfo.email());
        System.out.println("name       = " + userInfo.name());
    }

    private OAuthClient createClient(Provider provider) {
        return switch (provider) {
            case KAKAO -> new KakaoOAuthClient(new OAuthApiCaller(), kakaoAppId());
            case NAVER -> new NaverOAuthClient(new OAuthApiCaller());
            case GOOGLE -> new GoogleOAuthClient(OidcIdTokenVerifier.forGoogle(clientIds()));
            case APPLE -> new AppleOAuthClient(OidcIdTokenVerifier.forApple(clientIds()));
            case LOCAL -> throw new IllegalArgumentException("LOCAL은 소셜 제공자가 아닙니다.");
        };
    }

    private Long kakaoAppId() {
        String appId = System.getProperty("oauth.probe.kakao-app-id");
        return appId == null || appId.isBlank() ? null : Long.valueOf(appId.trim());
    }

    private Set<String> clientIds() {
        return Set.of(requireProperty("oauth.probe.client-ids").split(","));
    }

    private String requireProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("-D" + name + "=... 을 넣어주세요.");
        }
        return value.trim();
    }
}
