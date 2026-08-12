package com.freepets.global.apiPayload.code.status;

import com.freepets.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

public enum ErrorStatus implements BaseErrorCode {

    COMMON400(BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    COMMON401(UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    COMMON403(FORBIDDEN, "COMMON403", "접근 권한이 없습니다."),
    COMMON404(NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다."),
    COMMON500(INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorStatus(
            HttpStatus httpStatus,
            String code,
            String message
    ) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}