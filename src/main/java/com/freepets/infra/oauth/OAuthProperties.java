package com.freepets.infra.oauth;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 설정.
 *
 * <pre>
 * oauth:
 *   google:
 *     client-ids: 1234-ios.apps.googleusercontent.com,1234-android.apps.googleusercontent.com
 *   apple:
 *     client-ids: com.freepets.app
 *   kakao:
 *     app-id: 123456
 * </pre>
 *
 * <p>카카오·네이버는 액세스 토큰으로 userinfo API를 호출하므로 클라이언트 시크릿이 필요 없다.
 * 구글·애플은 id_token의 {@code aud}를 검증해야 해서 앱에 발급된 클라이언트 ID가 모두 필요하다.
 *
 * <p>여기 있는 세 값은 전부 <b>필수</b>다. 셋 다 "이 토큰이 우리 앱에서 발급된 것인가"를
 * 확인하는 데 쓰이고, 빠지면 다른 앱에서 발급된 토큰으로도 로그인이 되기 때문이다.
 * 하나라도 없으면 {@code OAuthConfig}가 기동을 막는다.
 *
 * <p>네이버만 대응하는 값이 없다. 네이버에는 카카오의 {@code access_token_info}에 해당하는
 * 공개 API가 없어 발급 앱을 확인할 방법이 없다 — 알려진 한계다.
 */
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
        Google google,
        Apple apple,
        Kakao kakao
) {

    /** @param clientIds iOS/Android/Web 등 앱에 발급된 OAuth 클라이언트 ID 전부 */
    public record Google(List<String> clientIds) {}

    /** @param clientIds 네이티브 앱 Bundle ID 및 웹 Service ID */
    public record Apple(List<String> clientIds) {}

    /**
     * @param appId 카카오 개발자 콘솔 &gt; 앱 설정 &gt; 요약 정보의 앱 ID. 액세스 토큰이 우리 앱에서
     *              발급된 것인지 대조하는 데 쓴다. 앱 키 4종과는 다른 값이다
     */
    public record Kakao(Long appId) {}
}
