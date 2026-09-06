package com.freepets.infra.oauth;

/**
 * 소셜 제공자 통신·검증 실패. infra 계층 예외이므로 HTTP 상태를 알지 못한다.
 * {@code AuthCommandService}에서 {@code ErrorStatus.OAUTH4002/OAUTH5001}로 옮긴다.
 */
public class OAuthException extends RuntimeException {

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
