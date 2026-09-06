package com.freepets.domain.auth.dto;

public class AuthResponseDTO {

    private AuthResponseDTO() {}

    /**
     * @param isNewUser 이번 요청으로 계정이 새로 만들어졌는지. 앱이 프로필 등록 화면으로
     *                  보낼지 결정하는 데 쓴다
     */
    public record SocialLoginResult(
            String accessToken,
            String refreshToken,
            boolean isNewUser
    ) {}
}
