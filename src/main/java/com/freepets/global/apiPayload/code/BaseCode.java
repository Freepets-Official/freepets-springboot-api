package com.freepets.global.apiPayload.code;

public interface BaseCode {

    boolean isSuccess();

    String getCode();

    String getMessage();

    default ReasonDTO getReasonDTO() {
        return new ReasonDTO(isSuccess(), getCode(), getMessage());
    }
}