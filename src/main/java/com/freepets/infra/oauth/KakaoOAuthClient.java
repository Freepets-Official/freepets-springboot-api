package com.freepets.infra.oauth;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.freepets.domain.user.entity.Provider;

/**
 * 카카오 액세스 토큰으로 사용자 정보를 조회한다.
 *
 * <p>카카오 액세스 토큰에는 "어느 앱에서 발급됐는지"가 담겨 있지 않다. 즉 다른 앱에서 발급된
 * 토큰으로도 {@code /v2/user/me}가 성공한다. 제3자 앱이 자기 사용자의 토큰을 우리 서버에
 * 던지면 그 사람의 이메일로 계정이 만들어질 수 있다(confused deputy).
 *
 * <p>그래서 {@code /v2/user/me}를 부르기 <b>전에</b> {@code /v1/user/access_token_info}로
 * 토큰이 우리 앱에서 발급된 것인지 먼저 확인한다. 구글·애플 id_token의 {@code aud} 검증과
 * 같은 역할이므로 건너뛸 수 없게 두고, 앱 ID가 없으면 {@code OAuthConfig}가 기동을 막는다.
 *
 * <p>로그인마다 호출이 한 번 늘지만, 그 대가로 막는 것이 "남의 계정 생성"이라 감수한다.
 */
public class KakaoOAuthClient implements OAuthClient {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String ACCESS_TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";

    private final OAuthApiCaller apiCaller;
    private final long expectedAppId;

    /**
     * @param expectedAppId 카카오 개발자 콘솔의 앱 ID. 필수다
     */
    public KakaoOAuthClient(
            OAuthApiCaller apiCaller,
            Long expectedAppId
    ) {
        this.apiCaller = Objects.requireNonNull(apiCaller);
        this.expectedAppId = Objects.requireNonNull(expectedAppId, "카카오 앱 ID는 필수입니다.");
    }

    @Override
    public Provider getProvider() {
        return Provider.KAKAO;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(
            String providerToken,
            String nameFromClient
    ) {
        verifyIssuedByOurApp(providerToken);
        return parseUserInfo(apiCaller.getWithBearerToken(USER_INFO_URI, providerToken));
    }

    /**
     * {@code /v2/user/me} 응답에서 식별자·이름·이메일을 꺼낸다.
     *
     * <p>이메일은 동의 항목이라 없을 수 있고, 있더라도 카카오가 유효하지 않다고 표시할 수 있다.
     * 확인되지 않은 이메일을 계정에 심으면 나중에 계정 연결의 근거로 오용될 수 있으므로
     * {@code is_email_valid}와 {@code is_email_verified}가 모두 참일 때만 받아들인다.
     */
    static OAuthUserInfo parseUserInfo(JsonNode response) {
        String providerId = OAuthJson.textOrNull(response, "id");
        JsonNode account = response.path("kakao_account");

        String name = OAuthJson.textOrNull(account, "profile", "nickname");
        String email = isEmailUsable(account) ? OAuthJson.textOrNull(account, "email") : null;

        return new OAuthUserInfo(providerId, email, name);
    }

    private static boolean isEmailUsable(JsonNode account) {
        return account.path("is_email_valid").asBoolean(false)
                && account.path("is_email_verified").asBoolean(false);
    }

    private void verifyIssuedByOurApp(String providerToken) {
        JsonNode tokenInfo = apiCaller.getWithBearerToken(ACCESS_TOKEN_INFO_URI, providerToken);

        // app_id가 없으면 -1이 되어 아래 비교에서 걸린다. 응답 형식이 바뀌었을 때
        // 검증이 조용히 통과하는 쪽으로 무너지지 않도록 기본값을 일부러 불일치 값으로 둔다.
        long appId = tokenInfo.path("app_id").asLong(-1L);

        if (appId != expectedAppId) {
            throw new OAuthException(
                    "다른 카카오 앱에서 발급된 액세스 토큰입니다. appId=" + appId + ", 기대값=" + expectedAppId
            );
        }
    }
}
