package com.freepets.domain.auth.dto;

public class AuthResponseDTO {

    private AuthResponseDTO() {}

    /**
     * @param userId    앱이 로그인 직후부터 사용자별 로컬 저장소를 가르는 데 쓴다. 문자열인
     *                  이유는 {@link TokenRefreshResult}와 같다
     * @param isNewUser 이번 요청으로 계정이 새로 만들어졌는지. 앱이 프로필 등록 화면으로
     *                  보낼지 결정하는 데 쓴다
     */
    public record SocialLoginResult(
            String userId,
            String accessToken,
            String refreshToken,
            boolean isNewUser
    ) {}

    /**
     * @param userId 명세가 문자열로 정의해 문자열로 내려보낸다. 서버 내부 타입은 {@code Long}이다
     */
    public record TokenRefreshResult(
            String userId,
            String accessToken,
            String refreshToken
    ) {}
}
