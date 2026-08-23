package com.freepets.infra.tourapi;

/**
 * 관광공사 API 호출 실패를 나타내는 예외.
 *
 * <p>{@code TourApiClient}가 스프링에 의존하지 않는 POJO이므로 전역 예외 체계 대신 별도 예외를 둔다.
 * 서비스 계층에서 잡아 {@code GeneralException}으로 변환한다.
 */
public class TourApiException extends RuntimeException {

    public TourApiException(String message) {
        super(message);
    }

    public TourApiException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }

}
