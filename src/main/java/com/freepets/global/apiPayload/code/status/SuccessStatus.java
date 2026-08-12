package com.freepets.global.apiPayload.code.status;

import com.freepets.global.apiPayload.code.BaseCode;

public enum SuccessStatus implements BaseCode {

    COMMON200("COMMON200", "요청에 성공했습니다.");

    private final String code;
    private final String message;

    SuccessStatus(
            String code,
            String message
    ) {
        this.code = code;
        this.message = message;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}