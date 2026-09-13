package com.freepets.infra.oauth;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 애플 토큰 API를 호출한다. 계정 삭제 시 토큰을 폐기하기 위해 필요하다.
 *
 * <p><b>{@link AppleOAuthClient}와 역할이 다르다.</b> 그쪽은 로그인할 때마다 id_token 서명을
 * 검증하고, 이 클래스는 최초 로그인과 탈퇴 때만 애플과 토큰을 주고받는다. id_token은 "이 사람이
 * 누구인지"를 증명하는 값일 뿐 폐기할 수 있는 대상이 아니라서, 폐기하려면 별도로 발급받아 둔
 * refresh token이 있어야 한다.
 *
 * <p>그래서 흐름이 두 단계다. 앱이 로그인 때 함께 보내주는 {@code authorizationCode}를
 * {@link #exchangeAuthorizationCode}로 refresh token과 바꿔 보관해두고, 탈퇴할 때 그 값을
 * {@link #revokeRefreshToken}으로 애플에 반납한다. 인가 코드는 5분 만료에 1회용이라 탈퇴
 * 시점에 새로 받을 수 없어, 로그인 때 받아두는 것 말고는 방법이 없다.
 *
 * <p>{@link OAuthClient}를 구현하지 않는다 — 그 인터페이스는 "토큰 하나로 사용자 정보를
 * 얻는다"는 계약이고 네 제공자가 공유하는데, 여기에 인가 코드를 끼워 넣으면 그 값을 쓰지 않는
 * 카카오·네이버·구글 구현체까지 시그니처가 흔들린다.
 */
public class AppleTokenClient {

    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";
    private static final String REFRESH_TOKEN_FIELD = "refresh_token";
    /** 애플이 "그 토큰은 이미 못 쓴다"고 알려줄 때 쓰는 오류 코드. */
    private static final String INVALID_GRANT_ERROR = "invalid_grant";

    private final OAuthApiCaller oAuthApiCaller;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String clientId;

    public AppleTokenClient(
            OAuthApiCaller oAuthApiCaller,
            AppleClientSecretGenerator clientSecretGenerator,
            String clientId
    ) {
        this.oAuthApiCaller = oAuthApiCaller;
        this.clientSecretGenerator = clientSecretGenerator;
        this.clientId = clientId;
    }

    /**
     * 인가 코드를 refresh token으로 바꾼다.
     *
     * @throws OAuthException 통신 실패, 애플의 거부(만료·재사용된 코드 등), 응답에 refresh token이 없는 경우
     */
    public String exchangeAuthorizationCode(String authorizationCode) {
        JsonNode response = oAuthApiCaller.postFormForJson(TOKEN_URI, Map.of(
                "client_id", clientId,
                "client_secret", clientSecretGenerator.generate(),
                "code", authorizationCode,
                "grant_type", "authorization_code"
        ));

        String refreshToken = OAuthJson.textOrNull(response, REFRESH_TOKEN_FIELD);
        if (refreshToken == null) {
            // 애플이 200을 주면서도 refresh token을 빼는 경우가 있다(이미 쓴 코드 등).
            // 폐기할 값이 없으면 보관해봐야 의미가 없으므로 성공으로 넘기지 않는다.
            throw new OAuthException("애플 토큰 응답에 refresh_token이 없습니다.");
        }
        return refreshToken;
    }

    /**
     * refresh token을 폐기한다. 이 토큰으로 발급된 액세스 토큰도 함께 무효가 된다.
     *
     * <p>성공하면 애플은 본문 없는 200을 준다 — 그래서 JSON을 읽지 않는 쪽으로 호출한다.
     *
     * <p><b>이미 무효한 토큰은 성공으로 친다.</b> 애플은 이 경우 {@code invalid_grant}로 거절하는데,
     * 목적이 "이 토큰을 더 못 쓰게 하는 것"이니 이미 그 상태라면 할 일이 없다. 이걸 실패로 보면
     * 영원히 폐기되지 않는 토큰으로 남아 재시도가 끝나지 않는다.
     *
     * @throws OAuthException 통신 실패, 또는 고치면 재시도할 수 있는 거부(설정 오류·애플 장애 등)
     */
    public void revokeRefreshToken(String refreshToken) {
        try {
            oAuthApiCaller.postForm(REVOKE_URI, Map.of(
                    "client_id", clientId,
                    "client_secret", clientSecretGenerator.generate(),
                    "token", refreshToken,
                    "token_type_hint", REFRESH_TOKEN_FIELD
            ));
        } catch (OAuthException exception) {
            if (isAlreadyInvalid(exception)) {
                return;
            }
            throw exception;
        }
    }

    /**
     * 애플이 토큰 자체를 무효로 판단했는지. {@code OAuthApiCaller}가 실패 메시지에 응답 본문을
     * 담아주므로 거기서 오류 코드를 읽는다 — 애플은 폐기 실패를 본문의 {@code error} 값으로만
     * 구분해주고 상태코드는 다 400이라 이 방법뿐이다.
     */
    private static boolean isAlreadyInvalid(OAuthException exception) {
        String message = exception.getMessage();
        return message != null && message.contains(INVALID_GRANT_ERROR);
    }
}
