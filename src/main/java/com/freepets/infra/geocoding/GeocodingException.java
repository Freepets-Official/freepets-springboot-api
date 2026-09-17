package com.freepets.infra.geocoding;

/**
 * 카카오 로컬 API 호출·응답 처리 실패.
 *
 * <p>HTTP 상태를 모른다. 서비스 계층에서 잡아 {@code GeneralException}으로 변환한다
 * ({@code NtsException}·{@code OAuthException}과 같은 방식).
 */
public class GeocodingException extends RuntimeException {

    public GeocodingException(String message) {
        super(message);
    }

    public GeocodingException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
