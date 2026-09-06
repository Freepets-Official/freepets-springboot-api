package com.freepets.infra.oauth;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 구글·애플이 발급한 OIDC id_token을 검증한다.
 *
 * <p>id_token은 RS256 서명이라 제공자의 JWKS 공개키로 검증해야 하고, 제공자는 키를 주기적으로
 * 교체한다. {@link NimbusJwtDecoder}가 JWKS 조회·키 회전 캐싱과 서명·만료 검증을 담당하고,
 * 여기서는 {@code iss}와 {@code aud} 검증을 얹는다.
 *
 * <p><b>{@code aud} 검증은 생략할 수 없다.</b> 서명만 확인하면 같은 제공자를 쓰는 아무 앱의
 * id_token이나 통과해 그 사용자로 로그인된다.
 *
 * <p>스프링에 의존하지 않는 POJO다. {@code OAuthConfig}가 빈으로 등록한다.
 * JWKS 캐시를 들고 있으므로 제공자당 하나만 만들어 재사용한다.
 */
public class OidcIdTokenVerifier {

    private final NimbusJwtDecoder jwtDecoder;

    /**
     * @param allowedAudiences 앱에 발급된 구글 OAuth 클라이언트 ID 전부 (iOS/Android/Web)
     */
    public static OidcIdTokenVerifier forGoogle(Set<String> allowedAudiences) {
        return new OidcIdTokenVerifier(OidcProvider.GOOGLE, allowedAudiences);
    }

    /**
     * @param allowedAudiences 네이티브 앱 Bundle ID 및 웹 Service ID
     */
    public static OidcIdTokenVerifier forApple(Set<String> allowedAudiences) {
        return new OidcIdTokenVerifier(OidcProvider.APPLE, allowedAudiences);
    }

    /**
     * @param allowedAudiences 허용할 {@code aud} 값. 앱에 발급된 클라이언트 ID 전부
     */
    private OidcIdTokenVerifier(
            OidcProvider oidcProvider,
            Set<String> allowedAudiences
    ) {
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            throw new IllegalArgumentException("허용 aud 목록이 비어 있습니다. provider=" + oidcProvider);
        }

        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(oidcProvider.getJwkSetUri()).build();
        this.jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                issuerValidator(oidcProvider.getAllowedIssuers()),
                audienceValidator(Set.copyOf(allowedAudiences))
        ));
    }

    /**
     * @throws OAuthException 서명·issuer·audience·만료 검증 중 하나라도 실패한 경우
     */
    public Jwt verify(String idToken) {
        try {
            return jwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw new OAuthException("id_token 검증에 실패했습니다. " + exception.getMessage(), exception);
        }
    }

    private static OAuth2TokenValidator<Jwt> issuerValidator(Set<String> allowedIssuers) {
        return token -> {
            String issuer = token.getIssuer() == null ? null : token.getIssuer().toString();
            if (issuer != null && allowedIssuers.contains(issuer)) {
                return OAuth2TokenValidatorResult.success();
            }
            return failure("id_token의 iss가 허용 목록에 없습니다. iss=" + issuer);
        };
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(Set<String> allowedAudiences) {
        return token -> {
            List<String> audiences = token.getAudience();
            if (audiences != null && audiences.stream().anyMatch(allowedAudiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return failure("id_token의 aud가 허용 목록에 없습니다. aud=" + audiences);
        };
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
