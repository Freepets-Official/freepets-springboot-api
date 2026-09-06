package com.freepets.infra.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.freepets.domain.user.entity.Provider;

/**
 * 네이버 액세스 토큰으로 사용자 정보를 조회한다.
 *
 * <p>네이버 응답은 실제 값이 {@code response} 아래에 한 겹 감싸여 오고, 성공 여부는
 * {@code resultcode}가 {@code "00"}인지로 판단한다.
 *
 * <p><b>알려진 한계</b> — 네이버 액세스 토큰에도 발급 앱 정보가 없는데, 카카오의
 * {@code access_token_info}에 대응하는 API가 없어 발급 앱을 검증할 방법이 없다.
 * 즉 다른 네이버 앱에서 발급된 토큰을 걸러내지 못한다(confused deputy). 감수하고 가는 부분이다.
 */
public class NaverOAuthClient implements OAuthClient {

    private static final String USER_INFO_URI = "https://openapi.naver.com/v1/nid/me";
    private static final String SUCCESS_RESULT_CODE = "00";

    private final OAuthApiCaller apiCaller;

    public NaverOAuthClient(OAuthApiCaller apiCaller) {
        this.apiCaller = apiCaller;
    }

    @Override
    public Provider getProvider() {
        return Provider.NAVER;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(
            String providerToken,
            String nameFromClient
    ) {
        return parseUserInfo(apiCaller.getWithBearerToken(USER_INFO_URI, providerToken));
    }

    /**
     * {@code /v1/nid/me} 응답에서 식별자·이름·이메일을 꺼낸다.
     *
     * <p>네이버는 토큰이 잘못돼도 HTTP 200에 {@code resultcode}만 다르게 주는 경우가 있어
     * 상태코드만 믿지 않고 여기서 한 번 더 확인한다.
     *
     * <p>이름은 {@code name}(실명)을 우선하고 없으면 {@code nickname}으로 물러난다.
     * 둘 다 동의 항목이라 없을 수 있다.
     */
    static OAuthUserInfo parseUserInfo(JsonNode response) {
        String resultCode = OAuthJson.textOrNull(response, "resultcode");
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            throw new OAuthException(
                    "네이버 사용자 조회가 실패했습니다. resultcode=" + resultCode
                            + ", message=" + OAuthJson.textOrNull(response, "message")
            );
        }

        JsonNode profile = response.path("response");

        String providerId = OAuthJson.textOrNull(profile, "id");
        String name = OAuthJson.textOrNull(profile, "name");
        if (name == null) {
            name = OAuthJson.textOrNull(profile, "nickname");
        }
        String email = OAuthJson.textOrNull(profile, "email");

        return new OAuthUserInfo(providerId, email, name);
    }
}
