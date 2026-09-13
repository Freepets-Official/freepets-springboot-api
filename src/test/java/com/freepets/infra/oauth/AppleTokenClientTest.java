package com.freepets.infra.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AppleTokenClientTest {

    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";
    private static final String CLIENT_ID = "com.freepets.app";
    private static final String REFRESH_TOKEN = "r.AdeadbeefAppleRefreshTokenValue";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OAuthApiCaller oAuthApiCaller;

    @Mock
    private AppleClientSecretGenerator clientSecretGenerator;

    private AppleTokenClient appleTokenClient;

    @BeforeEach
    void setUp() {
        appleTokenClient = new AppleTokenClient(oAuthApiCaller, clientSecretGenerator, CLIENT_ID);
    }

    @Test
    void 인가_코드를_refresh_token으로_바꾼다() throws Exception {
        when(clientSecretGenerator.generate()).thenReturn("client-secret-jwt");
        when(oAuthApiCaller.postFormForJson(eq(TOKEN_URI), any()))
                .thenReturn(objectMapper.readTree("{\"refresh_token\":\"" + REFRESH_TOKEN + "\"}"));

        assertThat(appleTokenClient.exchangeAuthorizationCode("auth-code")).isEqualTo(REFRESH_TOKEN);
    }

    @Test
    void 교환_요청에_애플이_요구하는_필드를_담는다() throws Exception {
        when(clientSecretGenerator.generate()).thenReturn("client-secret-jwt");
        when(oAuthApiCaller.postFormForJson(anyString(), any()))
                .thenReturn(objectMapper.readTree("{\"refresh_token\":\"" + REFRESH_TOKEN + "\"}"));

        appleTokenClient.exchangeAuthorizationCode("auth-code");

        assertThat(capturedFormOf(TOKEN_URI)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "client_id", CLIENT_ID,
                "client_secret", "client-secret-jwt",
                "code", "auth-code",
                "grant_type", "authorization_code"
        ));
    }

    // 애플이 200을 주면서도 refresh token을 빼는 경우가 있다. 폐기할 값이 없으면 보관할 이유가 없다.
    @Test
    void 응답에_refresh_token이_없으면_예외가_난다() throws Exception {
        when(clientSecretGenerator.generate()).thenReturn("client-secret-jwt");
        when(oAuthApiCaller.postFormForJson(anyString(), any()))
                .thenReturn(objectMapper.readTree("{\"access_token\":\"only-access\"}"));

        assertThrows(
                OAuthException.class,
                () -> appleTokenClient.exchangeAuthorizationCode("auth-code")
        );
    }

    @Test
    void 폐기_요청에_애플이_요구하는_필드를_담는다() {
        when(clientSecretGenerator.generate()).thenReturn("client-secret-jwt");
        doNothing().when(oAuthApiCaller).postForm(anyString(), any());

        appleTokenClient.revokeRefreshToken(REFRESH_TOKEN);

        assertThat(capturedFormOf(REVOKE_URI)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "client_id", CLIENT_ID,
                "client_secret", "client-secret-jwt",
                "token", REFRESH_TOKEN,
                "token_type_hint", "refresh_token"
        ));
    }

    // invalid_grant는 client_id가 토큰과 맞지 않을 때도 온다. 성공으로 넘기면 멀쩡히 살아 있는
    // 토큰을 폐기됐다고 보고 보관 행까지 지워, 재시도할 단서가 사라진다.
    @Test
    void invalid_grant_거부도_예외로_올린다() {
        when(clientSecretGenerator.generate()).thenReturn("client-secret-jwt");
        doThrow(new OAuthException(REVOKE_URI + " 호출이 거부되었습니다. HTTP 400 body={\"error\":\"invalid_grant\"}"))
                .when(oAuthApiCaller).postForm(anyString(), any());

        assertThrows(
                OAuthException.class,
                () -> appleTokenClient.revokeRefreshToken(REFRESH_TOKEN)
        );
    }

    @Test
    void 설정_오류_거부는_예외로_올린다() {
        when(clientSecretGenerator.generate()).thenReturn("client-secret-jwt");
        doThrow(new OAuthException(REVOKE_URI + " 호출이 거부되었습니다. HTTP 400 body={\"error\":\"invalid_client\"}"))
                .when(oAuthApiCaller).postForm(anyString(), any());

        assertThrows(
                OAuthException.class,
                () -> appleTokenClient.revokeRefreshToken(REFRESH_TOKEN)
        );
    }

    @Test
    void 통신_실패는_예외로_올린다() {
        when(clientSecretGenerator.generate()).thenReturn("client-secret-jwt");
        doThrow(new OAuthException(REVOKE_URI + " 호출에 실패했습니다."))
                .when(oAuthApiCaller).postForm(anyString(), any());

        assertThrows(
                OAuthException.class,
                () -> appleTokenClient.revokeRefreshToken(REFRESH_TOKEN)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> capturedFormOf(String expectedUri) {
        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> formCaptor = ArgumentCaptor.forClass(Map.class);

        if (REVOKE_URI.equals(expectedUri)) {
            verify(oAuthApiCaller).postForm(uriCaptor.capture(), formCaptor.capture());
        } else {
            verify(oAuthApiCaller).postFormForJson(uriCaptor.capture(), formCaptor.capture());
        }

        assertThat(uriCaptor.getValue()).isEqualTo(expectedUri);
        return formCaptor.getValue();
    }
}
