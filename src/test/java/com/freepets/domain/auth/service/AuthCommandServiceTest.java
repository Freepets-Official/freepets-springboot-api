package com.freepets.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.freepets.domain.user.service.AppleRefreshTokenService;
import com.freepets.domain.user.service.SocialUserResolution;
import com.freepets.domain.user.service.UserCommandService;
import com.freepets.domain.user.service.UserQueryService;
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
    private UserQueryService userQueryService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private OAuthClient oAuthClient;

    @Mock
    private AppleRefreshTokenService appleRefreshTokenService;

    @InjectMocks
    private AuthCommandService authCommandService;

    private AuthRequestDTO.SocialLoginRequest createRequest() {
        AuthRequestDTO.SocialLoginRequest request = new AuthRequestDTO.SocialLoginRequest();
        request.setProviderToken("provider-token");
        request.setName("홍길동");
        request.setAuthorizationCode("apple-auth-code");
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

    // 탈퇴 시 폐기할 토큰은 애플에만 필요하다. 다른 제공자까지 부르면 불필요한 외부 호출이 된다.
    @Test
    void 애플_로그인이면_인가_코드를_보관한다() {
        OAuthUserInfo userInfo = new OAuthUserInfo("apple-1", "foo@bar.com", "홍길동");
        givenVerifiedUser(Provider.APPLE, userInfo);
        givenIssuedTokens(5L);
        User user = createUser(5L);
        when(userCommandService.findOrRegisterSocialUser(
                Provider.APPLE, "apple-1", "foo@bar.com", "홍길동"
        )).thenReturn(new SocialUserResolution(user, true));

        authCommandService.socialLogin("apple", createRequest());

        verify(appleRefreshTokenService).storeFromAuthorizationCode(user, "apple-auth-code");
    }

    @Test
    void 애플이_아닌_로그인은_인가_코드를_보관하지_않는다() {
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-1", "foo@bar.com", "홍길동");
        givenVerifiedUser(Provider.KAKAO, userInfo);
        givenIssuedTokens(1L);
        when(userCommandService.findOrRegisterSocialUser(
                Provider.KAKAO, "kakao-1", "foo@bar.com", "홍길동"
        )).thenReturn(new SocialUserResolution(createUser(1L), false));

        authCommandService.socialLogin("kakao", createRequest());

        verifyNoInteractions(appleRefreshTokenService);
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
    @Test
    void 리프레시_토큰으로_액세스_토큰과_리프레시_토큰을_다시_발급한다() {
        when(jwtProvider.getUserIdFromRefreshToken("refresh-token")).thenReturn(7L);
        when(userQueryService.isActiveUser(7L)).thenReturn(true);
        when(jwtProvider.createAccessToken(7L)).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(7L)).thenReturn("new-refresh-token");

        AuthResponseDTO.TokenRefreshResult result = authCommandService.refreshToken("refresh-token");

        assertThat(result.userId()).isEqualTo("7");
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void 탈퇴한_계정의_리프레시_토큰이면_MEMBER4007을_던지고_토큰을_발급하지_않는다() {
        when(jwtProvider.getUserIdFromRefreshToken("refresh-token")).thenReturn(7L);
        when(userQueryService.isActiveUser(7L)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> authCommandService.refreshToken("refresh-token")
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4007);
        verify(jwtProvider, never()).createAccessToken(any());
    }

    @Test
    void 리프레시_토큰이_유효하지_않으면_계정_조회까지_가지_않는다() {
        when(jwtProvider.getUserIdFromRefreshToken("broken-token"))
                .thenThrow(new GeneralException(ErrorStatus.TOKEN4003));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> authCommandService.refreshToken("broken-token")
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.TOKEN4003);
        verify(userQueryService, never()).isActiveUser(any());
    }
}
