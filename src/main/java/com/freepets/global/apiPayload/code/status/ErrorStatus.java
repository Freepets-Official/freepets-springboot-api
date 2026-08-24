package com.freepets.global.apiPayload.code.status;

import com.freepets.global.apiPayload.code.BaseErrorCode;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

public enum ErrorStatus implements BaseErrorCode {

    COMMON400(BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    COMMON401(UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    COMMON403(FORBIDDEN, "COMMON403", "접근 권한이 없습니다."),
    COMMON404(NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다."),
    COMMON500(INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다."),

    // 회원 에러
    MEMBER4001(CONFLICT, "MEMBER4001", "이미 가입된 이메일입니다."),
    MEMBER4005(NOT_FOUND, "MEMBER4005", "해당 아이디를 가진 유저가 존재하지 않습니다."),
    MEMBER4006(UNAUTHORIZED, "MEMBER4006", "비밀번호가 일치하지 않습니다."),

    // 반려동물 에러
    PET4001(NOT_FOUND, "PET4001", "해당 반려동물이 존재하지 않습니다."),
    PET4002(FORBIDDEN, "PET4002", "해당 반려동물에 대한 권한이 없습니다."),

    // 토큰 에러
    TOKEN4001(UNAUTHORIZED, "TOKEN4001", "유효하지 않은 토큰입니다."),
    TOKEN4002(UNAUTHORIZED, "TOKEN4002", "만료된 토큰입니다."),

    // 이미지 에러
    IMAGE4001(BAD_REQUEST, "IMAGE4001", "이미지 파일이 비어있습니다."),
    IMAGE4002(BAD_REQUEST, "IMAGE4002", "파일 확장자가 없습니다."),
    IMAGE4003(BAD_REQUEST, "IMAGE4003", "지원하지 않는 이미지 확장자입니다. (jpg, jpeg, png, gif만 가능)"),
    IMAGE5001(INTERNAL_SERVER_ERROR, "IMAGE5001", "이미지 업로드 중 오류가 발생했습니다."),

    // 시설 에러
    FACILITY4041(NOT_FOUND, "FACILITY4041", "존재하지 않는 시설입니다.");

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