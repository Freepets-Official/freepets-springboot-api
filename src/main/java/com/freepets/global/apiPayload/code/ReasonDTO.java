package com.freepets.global.apiPayload.code;

public record ReasonDTO(
        boolean isSuccess,
        String code,
        String message
) {
}