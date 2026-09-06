package com.freepets.infra.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 카카오 토큰의 발급 앱 대조 검증.
 *
 * <p>카카오 액세스 토큰에는 발급 앱 정보가 없어 다른 앱에서 발급된 토큰으로도
 * {@code /v2/user/me}가 성공한다. 그 구멍을 막는 게 이 검사이므로,
 * <b>어떤 경로로도 건너뛰어지지 않는다는 것</b>을 여기서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class KakaoOAuthClientTest {

    private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private static final long OUR_APP_ID = 1234567L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OAuthApiCaller apiCaller;

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private KakaoOAuthClient createClient() {
        return new KakaoOAuthClient(apiCaller, OUR_APP_ID);
    }

    private void givenTokenInfo(String json) {
        when(apiCaller.getWithBearerToken(eq(TOKEN_INFO_URI), eq("token"))).thenReturn(read(json));
    }

    @Test
    void 우리_앱에서_발급된_토큰이면_사용자_정보를_반환한다() {
        givenTokenInfo("{\"id\": 1, \"app_id\": " + OUR_APP_ID + "}");
        when(apiCaller.getWithBearerToken(eq(USER_INFO_URI), eq("token"))).thenReturn(read("""
                {
                  "id": 1,
                  "kakao_account": { "profile": { "nickname": "홍길동" } }
                }
                """));

        OAuthUserInfo userInfo = createClient().fetchUserInfo("token", null);

        assertThat(userInfo.providerId()).isEqualTo("1");
        assertThat(userInfo.name()).isEqualTo("홍길동");
    }

    @Test
    void 다른_앱에서_발급된_토큰은_거부하고_사용자_정보를_조회하지_않는다() {
        givenTokenInfo("{\"id\": 1, \"app_id\": 9999999}");

        KakaoOAuthClient client = createClient();

        assertThrows(OAuthException.class, () -> client.fetchUserInfo("token", null));
        // 거부는 /v2/user/me를 부르기 전에 끝나야 한다. 남의 프로필을 읽어온 뒤 버리는 게 아니다.
        verify(apiCaller, never()).getWithBearerToken(eq(USER_INFO_URI), eq("token"));
    }

    @Test
    void app_id가_응답에_없으면_통과시키지_않는다() {
        // 카카오 응답 형식이 바뀌어도 검증이 조용히 무력화되면 안 된다.
        givenTokenInfo("{\"id\": 1}");

        KakaoOAuthClient client = createClient();

        assertThrows(OAuthException.class, () -> client.fetchUserInfo("token", null));
        verify(apiCaller, never()).getWithBearerToken(eq(USER_INFO_URI), eq("token"));
    }

    @Test
    void 앱_ID_없이는_클라이언트를_만들_수_없다() {
        // 설정 누락으로 검증이 꺼지는 경로를 아예 없앤다.
        assertThrows(
                NullPointerException.class,
                () -> new KakaoOAuthClient(apiCaller, null)
        );
    }
}