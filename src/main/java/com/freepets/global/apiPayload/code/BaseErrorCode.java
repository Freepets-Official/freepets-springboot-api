package com.freepets.global.apiPayload.code;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode extends BaseCode {

    HttpStatus getHttpStatus();
}