package com.freepets.domain.auth.service;

import org.springframework.stereotype.Service;

import com.freepets.domain.auth.converter.AuthConverter;
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
    private final JwtProvider jwtProvider;

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
        return AuthConverter.toSocialLoginResult(
                jwtProvider.createAccessToken(user.getId()),
                jwtProvider.createRefreshToken(user.getId()),
                resolution.isNewUser()
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
