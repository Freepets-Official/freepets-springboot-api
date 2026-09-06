package com.freepets.global.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.freepets.infra.oauth.AppleOAuthClient;
import com.freepets.infra.oauth.GoogleOAuthClient;
import com.freepets.infra.oauth.KakaoOAuthClient;
import com.freepets.infra.oauth.NaverOAuthClient;
import com.freepets.infra.oauth.OAuthApiCaller;
import com.freepets.infra.oauth.OAuthClient;
import com.freepets.infra.oauth.OAuthClientRegistry;
import com.freepets.infra.oauth.OAuthProperties;
import com.freepets.infra.oauth.OidcIdTokenVerifier;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 클라이언트 빈 등록.
 *
 * <p><b>설정이 없으면 애플리케이션을 기동하지 않는다.</b> {@code TourApiConfig}는 반대로
 * {@code @Lazy}로 기동을 막지 않는데, 그건 인증키가 없어도 서버가 떠야 하는 배치 기능이기
 * 때문이다. 소셜 로그인은 핵심 기능이라 설정이 빠진 채 조용히 뜨는 쪽이 더 나쁘다.
 * 첫 로그인 요청에서 500을 만나고 원인을 찾는 대신, 여기서 어떤 키가 비었는지 알리고 멈춘다.
 */
@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
@RequiredArgsConstructor
public class OAuthConfig {

    private final OAuthProperties oAuthProperties;

    @Bean
    public OAuthApiCaller oAuthApiCaller() {
        return new OAuthApiCaller();
    }

    @Bean
    public KakaoOAuthClient kakaoOAuthClient(OAuthApiCaller oAuthApiCaller) {
        return new KakaoOAuthClient(oAuthApiCaller, requireKakaoAppId());
    }

    @Bean
    public NaverOAuthClient naverOAuthClient(OAuthApiCaller oAuthApiCaller) {
        return new NaverOAuthClient(oAuthApiCaller);
    }

    @Bean
    public GoogleOAuthClient googleOAuthClient() {
        return new GoogleOAuthClient(OidcIdTokenVerifier.forGoogle(
                requireClientIds(
                        oAuthProperties.google() == null ? null : oAuthProperties.google().clientIds(),
                        "oauth.google.client-ids"
                )
        ));
    }

    @Bean
    public AppleOAuthClient appleOAuthClient() {
        return new AppleOAuthClient(OidcIdTokenVerifier.forApple(
                requireClientIds(
                        oAuthProperties.apple() == null ? null : oAuthProperties.apple().clientIds(),
                        "oauth.apple.client-ids"
                )
        ));
    }

    @Bean
    public OAuthClientRegistry oAuthClientRegistry(List<OAuthClient> oAuthClients) {
        return new OAuthClientRegistry(oAuthClients);
    }

    /**
     * 카카오 액세스 토큰의 발급 앱 대조에 쓸 앱 ID를 확인한다.
     *
     * <p>카카오 액세스 토큰에는 발급 앱 정보가 없어서, 이 값이 없으면 다른 카카오 앱에서 발급된
     * 토큰도 그대로 통과한다. 제3자 앱이 자기 사용자의 토큰을 우리 서버에 던져 그 사람 이메일로
     * 계정을 만들 수 있다는 뜻이다. 구글·애플의 {@code aud} 검증과 성격이 같은 통제이므로
     * 똑같이 필수로 두고, 없으면 기동을 막는다.
     */
    private Long requireKakaoAppId() {
        Long appId = oAuthProperties.kakao() == null ? null : oAuthProperties.kakao().appId();

        if (appId == null) {
            throw new IllegalStateException(
                    "oauth.kakao.app-id 설정이 없습니다. 카카오 개발자 콘솔 > 앱 설정 > 요약 정보의 앱 ID를 "
                            + "application.yml에 넣어주세요. 액세스 토큰이 우리 앱에서 발급된 것인지 확인하는 데 "
                            + "필요하며, 없으면 다른 카카오 앱에서 발급된 토큰으로도 로그인됩니다."
            );
        }
        return appId;
    }

    /**
     * id_token의 {@code aud} 검증에 쓸 클라이언트 ID 목록을 확인한다.
     * 비어 있으면 서명만 보고 아무 앱의 토큰이나 통과시키는 상태가 되므로 기동을 막는다.
     */
    private Set<String> requireClientIds(
            List<String> clientIds,
            String configKey
    ) {
        if (clientIds == null || clientIds.isEmpty()) {
            throw new IllegalStateException(
                    configKey + " 설정이 없습니다. application.yml에 앱의 OAuth 클라이언트 ID를 넣어주세요. "
                            + "id_token의 aud 검증에 필요하며, 없으면 다른 앱에서 발급된 토큰도 통과합니다."
            );
        }

        Set<String> trimmed = clientIds.stream()
                .filter(clientId -> clientId != null && !clientId.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());

        if (trimmed.isEmpty()) {
            throw new IllegalStateException(configKey + " 설정에 빈 값만 들어 있습니다.");
        }
        return trimmed;
    }
}
