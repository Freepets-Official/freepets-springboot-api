package com.freepets.global.security;

import java.io.IOException;

import org.springframework.http.MediaType;

import com.freepets.global.apiPayload.code.BaseErrorCode;

import jakarta.servlet.http.HttpServletResponse;

public final class SecurityResponseWriter {

    private SecurityResponseWriter() {}

    public static void write(
            HttpServletResponse response,
            BaseErrorCode errorCode
    ) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"isSuccess\":false,\"code\":\"%s\",\"message\":\"%s\"}"
                        .formatted(errorCode.getCode(), errorCode.getMessage())
        );
    }
}
