package com.freepets.domain.auth.converter;

import com.freepets.domain.auth.dto.AuthResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import java.util.Arrays;

public class AuthConverter {

    private AuthConverter() {}

    /**
     * 소셜 로그인 경로 변수({@code /api/v1/auth/social/{provider}})를 {@link Provider}로 옮긴다.
     *
     * <p>컨트롤러에서 {@code @PathVariable Provider}로 바로 바인딩하면 잘못된 값에서
     * {@code MethodArgumentTypeMismatchException}이 나고 {@code GlobalExceptionHandler}가
     * 잡지 못해 500이 된다. 그래서 문자열로 받아 여기서 400({@code OAUTH4001})으로 바꾼다.
     *
     * <p>{@code LOCAL}은 소셜 제공자가 아니므로 경로로 들어오면 거부한다.
     */
    public static Provider toSocialProvider(String pathVariable) {
        if (pathVariable == null || pathVariable.isBlank()) {
            throw new GeneralException(ErrorStatus.OAUTH4001);
        }

        return Arrays.stream(Provider.values())
                .filter(Provider::isSocial)
                .filter(provider -> provider.name().equalsIgnoreCase(pathVariable.trim()))
                .findFirst()
                .orElseThrow(() -> new GeneralException(ErrorStatus.OAUTH4001));
    }

    public static AuthResponseDTO.SocialLoginResult toSocialLoginResult(
            String accessToken,
            String refreshToken,
            boolean isNewUser
    ) {
        return new AuthResponseDTO.SocialLoginResult(accessToken, refreshToken, isNewUser);
    }

    public static AuthResponseDTO.TokenRefreshResult toTokenRefreshResult(
            Long userId,
            String accessToken,
            String refreshToken
    ) {
        return new AuthResponseDTO.TokenRefreshResult(String.valueOf(userId), accessToken, refreshToken);
    }
}
