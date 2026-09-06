package com.freepets.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.auth.dto.AuthRequestDTO;
import com.freepets.domain.auth.dto.AuthResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.service.SocialUserResolution;
import com.freepets.domain.user.service.UserCommandService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.security.jwt.JwtProvider;
import com.freepets.infra.oauth.OAuthClient;
import com.freepets.infra.oauth.OAuthClientRegistry;
import com.freepets.infra.oauth.OAuthException;
import com.freepets.infra.oauth.OAuthUserInfo;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    @Mock
    private OAuthClientRegistry oAuthClientRegistry;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private OAuthClient oAuthClient;

    @InjectMocks
    private AuthCommandService authCommandService;

    private AuthRequestDTO.SocialLoginRequest createRequest() {
        AuthRequestDTO.SocialLoginRequest request = new AuthRequestDTO.SocialLoginRequest();
        request.setProviderToken("provider-token");
        request.setName("홍길동");
        return request;
    }

    private void givenVerifiedUser(
            Provider provider,
            OAuthUserInfo userInfo
    ) {
        when(oAuthClientRegistry.find(provider)).thenReturn(oAuthClient);
        when(oAuthClient.fetchUserInfo(any(), any())).thenReturn(userInfo);
    }

    private void givenIssuedTokens(Long userId) {
        when(jwtProvider.createAccessToken(userId)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(userId)).thenReturn("refresh-token");
    }

    private User createUser(Long id) {
        User user = User.builder()
                .email("foo@bar.com")
                .nickname("홍길동")
                .provider(Provider.KAKAO)
                .providerId("kakao-1")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void 신규_사용자는_가입되고_isNewUser가_true다() {
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-1", "foo@bar.com", "홍길동");
        givenVerifiedUser(Provider.KAKAO, userInfo);
        givenIssuedTokens(1L);
        when(userCommandService.findOrRegisterSocialUser(
                Provider.KAKAO, "kakao-1", "foo@bar.com", "홍길동"
        )).thenReturn(new SocialUserResolution(createUser(1L), true));

        AuthResponseDTO.SocialLoginResult result =
                authCommandService.socialLogin("kakao", createRequest());

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void 기존_사용자는_isNewUser가_false다() {
        OAuthUserInfo userInfo = new OAuthUserInfo("google-1", "foo@bar.com", "홍길동");
        givenVerifiedUser(Provider.GOOGLE, userInfo);
        givenIssuedTokens(7L);
        when(userCommandService.findOrRegisterSocialUser(
                Provider.GOOGLE, "google-1", "foo@bar.com", "홍길동"
        )).thenReturn(new SocialUserResolution(createUser(7L), false));

        AuthResponseDTO.SocialLoginResult result =
                authCommandService.socialLogin("google", createRequest());

        assertThat(result.isNewUser()).isFalse();
    }

    @Test
    void provider_경로가_대소문자_달라도_처리한다() {
        OAuthUserInfo userInfo = new OAuthUserInfo("apple-1", null, "홍길동");
        givenVerifiedUser(Provider.APPLE, userInfo);
        givenIssuedTokens(3L);
        when(userCommandService.findOrRegisterSocialUser(
                Provider.APPLE, "apple-1", null, "홍길동"
        )).thenReturn(new SocialUserResolution(createUser(3L), true));

        assertThat(authCommandService.socialLogin("APPLE", createRequest())).isNotNull();
    }

    @Test
    void 지원하지_않는_provider_경로는_OAUTH4001을_던진다() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> authCommandService.socialLogin("facebook", createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.OAUTH4001);
        verify(userCommandService, never()).findOrRegisterSocialUser(any(), any(), any(), any());
    }

    @Test
    void local은_소셜_경로로_들어와도_거부한다() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> authCommandService.socialLogin("local", createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.OAUTH4001);
    }

    @Test
    void 토큰_검증이_실패하면_OAUTH4002를_던지고_가입시키지_않는다() {
        when(oAuthClientRegistry.find(Provider.NAVER)).thenReturn(oAuthClient);
        when(oAuthClient.fetchUserInfo(any(), any()))
                .thenThrow(new OAuthException("토큰이 만료됐습니다."));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> authCommandService.socialLogin("naver", createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.OAUTH4002);
        verify(userCommandService, never()).findOrRegisterSocialUser(any(), any(), any(), any());
    }

    @Test
    void 클라이언트가_등록되지_않은_provider는_OAUTH4001을_던진다() {
        when(oAuthClientRegistry.find(eq(Provider.KAKAO))).thenReturn(null);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> authCommandService.socialLogin("kakao", createRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.OAUTH4001);
    }
}
