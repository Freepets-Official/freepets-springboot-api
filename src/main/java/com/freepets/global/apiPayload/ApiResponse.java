package com.freepets.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.freepets.global.apiPayload.code.BaseCode;
import com.freepets.global.apiPayload.code.BaseErrorCode;
import com.freepets.global.apiPayload.code.ReasonDTO;
import com.freepets.global.apiPayload.code.status.SuccessStatus;

@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class ApiResponse<T> {

    private final Boolean isSuccess;
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T result;

    private ApiResponse(
            Boolean isSuccess,
            String code,
            String message,
            T result
    ) {
        this.isSuccess = isSuccess;
        this.code = code;
        this.message = message;
        this.result = result;
    }

    public static <T> ApiResponse<T> onSuccess(T result) {
        return new ApiResponse<>(true, SuccessStatus.COMMON200.getCode(), SuccessStatus.COMMON200.getMessage(), result);
    }

    public static <T> ApiResponse<T> onSuccess(
            BaseCode code,
            T result
    ) {
        ReasonDTO reason = code.getReasonDTO();
        return new ApiResponse<>(reason.isSuccess(), reason.code(), reason.message(), result);
    }

    public static <T> ApiResponse<T> onFailure(BaseErrorCode code) {
        return onFailure(code, null);
    }

    public static <T> ApiResponse<T> onFailure(
            BaseErrorCode code,
            T result
    ) {
        ReasonDTO reason = code.getReasonDTO();
        return new ApiResponse<>(reason.isSuccess(), reason.code(), reason.message(), result);
    }

    // Jackson의 boolean getter 이름 변환(isSuccess() -> "success")을 피하기 위해 필드명을 명시적으로 고정
    @JsonProperty("isSuccess")
    public Boolean getIsSuccess() {
        return isSuccess;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getResult() {
        return result;
    }
}