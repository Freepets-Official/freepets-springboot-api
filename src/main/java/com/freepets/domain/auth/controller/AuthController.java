package com.freepets.domain.auth.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.auth.dto.AuthRequestDTO;
import com.freepets.domain.auth.dto.AuthResponseDTO;
import com.freepets.domain.auth.service.AuthCommandService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthCommandService authCommandService;

    /**
     * 소셜 로그인. 계정이 없으면 그 자리에서 가입시키므로 별도 회원가입 API는 없다.
     *
     * @param provider {@code kakao}, {@code naver}, {@code google}, {@code apple}.
     *                 enum으로 직접 바인딩하지 않는 이유는 {@code AuthConverter} 주석 참고
     */
    @PostMapping("/social/{provider}")
    public ApiResponse<AuthResponseDTO.SocialLoginResult> socialLogin(
            @PathVariable String provider,
            @Valid @RequestBody AuthRequestDTO.SocialLoginRequest request
    ) {
        return ApiResponse.onSuccess(
                authCommandService.socialLogin(provider, request)
        );
    }

    /**
     * 액세스 토큰 재발급. 리프레시 토큰까지 새로 내려주므로 앱은 두 값을 모두 저장해야 한다.
     *
     * @param refreshToken {@code Authorization}이 아닌 전용 헤더로 받는다. {@code Authorization}으로
     *                     받으면 {@code JwtAuthenticationFilter}가 먼저 인증용으로 해석해버린다
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponseDTO.TokenRefreshResult> refreshToken(
            @RequestHeader("RefreshToken") String refreshToken
    ) {
        return ApiResponse.onSuccess(
                authCommandService.refreshToken(refreshToken)
        );
    }
}
