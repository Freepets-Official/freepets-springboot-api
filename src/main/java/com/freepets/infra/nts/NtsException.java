package com.freepets.infra.nts;

/**
 * 국세청 API 호출·응답 처리 실패.
 *
 * <p>HTTP 상태를 모른다. 서비스 계층에서 잡아 {@code GeneralException}으로 변환한다
 * ({@code TourApiException}·{@code OAuthException}과 같은 방식).
 */
public class NtsException extends RuntimeException {

    public NtsException(String message) {
        super(message);
    }

    public NtsException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
