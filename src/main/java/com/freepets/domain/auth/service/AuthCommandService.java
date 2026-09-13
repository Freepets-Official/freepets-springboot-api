package com.freepets.domain.auth.service;

import org.springframework.stereotype.Service;

import com.freepets.domain.auth.converter.AuthConverter;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 소셜 로그인. 앱이 소셜 SDK로 받은 토큰을 검증해 사용자를 찾거나 만들고, 자체 JWT를 발급한다.
 *
 * <p><b>클래스에 {@code @Transactional}을 걸지 않는다.</b> 이 흐름의 앞부분은 소셜 제공자를
 * 호출하는 네트워크 대기이고, 그동안 DB 커넥션을 잡고 있을 이유가 없다. 트랜잭션이 필요한
 * 조회·저장은 {@link UserCommandService#findOrRegisterSocialUser}가 자기 트랜잭션에서 처리한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthCommandService {

    private final OAuthClientRegistry oAuthClientRegistry;
    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;
    private final JwtProvider jwtProvider;
    private final AppleRefreshTokenService appleRefreshTokenService;

    public AuthResponseDTO.SocialLoginResult socialLogin(
            String providerPathVariable,
            AuthRequestDTO.SocialLoginRequest request
    ) {
        Provider provider = AuthConverter.toSocialProvider(providerPathVariable);
        OAuthUserInfo userInfo = fetchUserInfo(provider, request);

        SocialUserResolution resolution = userCommandService.findOrRegisterSocialUser(
                provider,
                userInfo.providerId(),
                userInfo.email(),
                userInfo.name()
        );

        User user = resolution.user();

        // 애플만: 탈퇴할 때 폐기할 refresh token을 미리 확보해둔다. 실패해도 로그인은 그대로
        // 진행된다 — 애플이 흔들렸다고 로그인 자체가 막히는 쪽이 훨씬 나쁘고, 이 토큰은
        // 탈퇴 시점에나 필요한 값이다(AppleRefreshTokenService 참고).
        if (provider == Provider.APPLE) {
            appleRefreshTokenService.storeFromAuthorizationCode(user, request.getAuthorizationCode());
        }

        return AuthConverter.toSocialLoginResult(
                jwtProvider.createAccessToken(user.getId()),
                jwtProvider.createRefreshToken(user.getId()),
                resolution.isNewUser()
        );
    }

    /**
     * 리프레시 토큰으로 액세스 토큰을 다시 내준다. 리프레시 토큰도 함께 새로 발급하므로
     * 앱은 응답의 두 값을 모두 저장해야 한다.
     *
     * <p>리프레시 토큰을 서버에 저장하지 않기 때문에 검사할 수 있는 것은 서명·만료·용도와
     * 계정의 생존 여부뿐이다. 즉 <b>직전 리프레시 토큰도 만료 전까지 그대로 유효하고</b>,
     * 탈취된 토큰을 개별적으로 끊을 방법은 없다. 토큰 폐기가 필요해지면 저장소를 붙여야 한다.
     */
    public AuthResponseDTO.TokenRefreshResult refreshToken(String refreshToken) {
        Long userId = jwtProvider.getUserIdFromRefreshToken(refreshToken);

        // 서명이 유효해도 그 사이 탈퇴했을 수 있다. 탈퇴한 계정에 새 액세스 토큰을 내주면
        // 탈퇴가 사실상 무효가 된다.
        if (!userQueryService.isActiveUser(userId)) {
            throw new GeneralException(ErrorStatus.MEMBER4007);
        }

        return AuthConverter.toTokenRefreshResult(
                userId,
                jwtProvider.createAccessToken(userId),
                jwtProvider.createRefreshToken(userId)
        );
    }

    /**
     * infra 계층의 {@link OAuthException}을 HTTP 응답으로 옮긴다.
     *
     * <p>토큰 검증 실패와 제공자 통신 실패를 구분하지 않고 모두 {@code OAUTH4002}로 내린다.
     * 클라이언트가 할 수 있는 조치가 "다시 로그인"으로 같고, 원인을 세분해 알려주면
     * 유효한 토큰을 탐색하는 데 힌트가 되기 때문이다. 원인은 로그로 남는다.
     */
    private OAuthUserInfo fetchUserInfo(
            Provider provider,
            AuthRequestDTO.SocialLoginRequest request
    ) {
        OAuthClient oAuthClient = oAuthClientRegistry.find(provider);
        if (oAuthClient == null) {
            throw new GeneralException(ErrorStatus.OAUTH4001);
        }

        try {
            return oAuthClient.fetchUserInfo(request.getProviderToken(), request.getName());
        } catch (OAuthException exception) {
            log.warn("{} 소셜 로그인 토큰 검증 실패: {}", provider, exception.getMessage(), exception);
            throw new GeneralException(ErrorStatus.OAUTH4002);
        }
    }
}
