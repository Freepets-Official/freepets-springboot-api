package com.freepets.global.apiPayload.exception;

import com.freepets.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode errorCode;
    private final Object result;

    public GeneralException(BaseErrorCode errorCode) {
        this(errorCode, null);
    }

    public GeneralException(
            BaseErrorCode errorCode,
            Object result
    ) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.result = result;
    }
}