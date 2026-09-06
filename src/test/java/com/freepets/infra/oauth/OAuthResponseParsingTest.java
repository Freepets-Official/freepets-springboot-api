package com.freepets.infra.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 카카오·네이버 userinfo 응답 파싱 검증.
 *
 * <p>HTTP 호출부({@link OAuthApiCaller})와 파싱부를 분리해 뒀으므로 실제 응답 모양의
 * JSON 문자열만으로 파싱 규칙을 검증할 수 있다. 실제 토큰이 필요한 확인은
 * {@code ./gradlew oauthProbe}로 따로 한다.
 */
class OAuthResponseParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void 카카오_응답에서_회원번호와_이름과_이메일을_꺼낸다() {
        JsonNode response = read("""
                {
                  "id": 1234567890,
                  "kakao_account": {
                    "profile": { "nickname": "홍길동" },
                    "is_email_valid": true,
                    "is_email_verified": true,
                    "email": "foo@bar.com"
                  }
                }
                """);

        OAuthUserInfo userInfo = KakaoOAuthClient.parseUserInfo(response);

        // 카카오 회원번호는 숫자로 오지만 provider별 형식 차이를 흡수하려고 문자열로 통일한다.
        assertThat(userInfo.providerId()).isEqualTo("1234567890");
        assertThat(userInfo.name()).isEqualTo("홍길동");
        assertThat(userInfo.email()).isEqualTo("foo@bar.com");
    }

    @Test
    void 카카오_이메일이_미인증이면_받지_않는다() {
        JsonNode response = read("""
                {
                  "id": 1,
                  "kakao_account": {
                    "profile": { "nickname": "홍길동" },
                    "is_email_valid": true,
                    "is_email_verified": false,
                    "email": "unverified@bar.com"
                  }
                }
                """);

        assertThat(KakaoOAuthClient.parseUserInfo(response).email()).isNull();
    }

    @Test
    void 카카오_이메일_동의를_거부하면_null이다() {
        JsonNode response = read("""
                {
                  "id": 1,
                  "kakao_account": { "profile": { "nickname": "홍길동" } }
                }
                """);

        assertThat(KakaoOAuthClient.parseUserInfo(response).email()).isNull();
    }

    @Test
    void 카카오_프로필_동의를_거부하면_이름이_null이다() {
        JsonNode response = read("""
                {
                  "id": 1,
                  "kakao_account": { "profile_nickname_needs_agreement": true }
                }
                """);

        assertThat(KakaoOAuthClient.parseUserInfo(response).name()).isNull();
    }

    @Test
    void JSON_null은_문자열_null이_아니라_자바_null로_읽는다() {
        JsonNode response = read("""
                {
                  "id": 1,
                  "kakao_account": {
                    "profile": { "nickname": null },
                    "is_email_valid": true,
                    "is_email_verified": true,
                    "email": null
                  }
                }
                """);

        OAuthUserInfo userInfo = KakaoOAuthClient.parseUserInfo(response);

        assertThat(userInfo.name()).isNull();
        assertThat(userInfo.email()).isNull();
    }

    @Test
    void 식별자가_없는_응답은_거부한다() {
        JsonNode response = read("""
                { "kakao_account": { "profile": { "nickname": "홍길동" } } }
                """);

        assertThrows(OAuthException.class, () -> KakaoOAuthClient.parseUserInfo(response));
    }

    @Test
    void 네이버_응답에서_식별자와_이름과_이메일을_꺼낸다() {
        JsonNode response = read("""
                {
                  "resultcode": "00",
                  "message": "success",
                  "response": {
                    "id": "abc123XYZ",
                    "name": "홍길동",
                    "email": "foo@bar.com"
                  }
                }
                """);

        OAuthUserInfo userInfo = NaverOAuthClient.parseUserInfo(response);

        assertThat(userInfo.providerId()).isEqualTo("abc123XYZ");
        assertThat(userInfo.name()).isEqualTo("홍길동");
        assertThat(userInfo.email()).isEqualTo("foo@bar.com");
    }

    @Test
    void 네이버_실명_동의가_없으면_닉네임으로_물러난다() {
        JsonNode response = read("""
                {
                  "resultcode": "00",
                  "response": { "id": "abc123", "nickname": "길동이" }
                }
                """);

        assertThat(NaverOAuthClient.parseUserInfo(response).name()).isEqualTo("길동이");
    }

    @Test
    void 네이버는_HTTP_200이어도_resultcode가_다르면_거부한다() {
        JsonNode response = read("""
                { "resultcode": "024", "message": "Authentication failed" }
                """);

        assertThrows(OAuthException.class, () -> NaverOAuthClient.parseUserInfo(response));
    }
}
