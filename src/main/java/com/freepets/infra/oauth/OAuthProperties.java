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

    /**
     * 애플은 두 가지 용도로 설정이 나뉜다.
     *
     * <p>{@code clientIds}는 로그인할 때마다 쓰는 <b>필수</b> 값이다(id_token의 {@code aud} 검증).
     *
     * <p>나머지는 계정 삭제 시 애플에 토큰 폐기를 요청하기 위한 <b>선택</b> 값이다. 넷이 모두
     * 채워졌을 때만 폐기 기능이 켜지고, 하나라도 비면 로그인은 그대로 되면서 토큰 교환·폐기만
     * 조용히 건너뛴다 — 키 발급 전에도 서버가 떠야 하기 때문이다({@code OAuthConfig} 참고).
     *
     * @param clientIds                네이티브 앱 Bundle ID 및 웹 Service ID
     * @param teamId                   Apple Developer 멤버십의 Team ID. client_secret의 {@code iss}
     * @param keyId                    Sign in with Apple 키의 Key ID. client_secret 헤더의 {@code kid}
     * @param privateKey               그 키의 {@code .p8} 내용. 진짜 여러 줄 PEM, 줄바꿈이
     *                                 이스케이프 문자로 들어간 PEM, 헤더를 떼어낸 한 줄 Base64
     *                                 어느 형태든 받는다({@code AppleClientSecretGenerator} 참고)
     * @param tokenEncryptionPassword  애플 refresh token을 DB에 암호화해 저장할 때 쓸 비밀번호
     * @param tokenEncryptionSalt      같은 용도의 솔트. 16진수 문자열이어야 한다
     */
    public record Apple(
            List<String> clientIds,
            String teamId,
            String keyId,
            String privateKey,
            String tokenEncryptionPassword,
            String tokenEncryptionSalt
    ) {}

    /**
     * @param appId 카카오 개발자 콘솔 &gt; 앱 설정 &gt; 요약 정보의 앱 ID. 액세스 토큰이 우리 앱에서
     *              발급된 것인지 대조하는 데 쓴다. 앱 키 4종과는 다른 값이다
     */
    public record Kakao(Long appId) {}
}
