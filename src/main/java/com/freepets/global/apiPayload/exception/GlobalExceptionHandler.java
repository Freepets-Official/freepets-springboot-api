package com.freepets.global.apiPayload.exception;

import com.freepets.global.apiPayload.ApiResponse;
import com.freepets.global.apiPayload.code.BaseErrorCode;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 어느 필드인지 짚을 수 없을 때 쓰는 키. */
    private static final String UNKNOWN_FIELD = "body";

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(GeneralException exception) {
        BaseErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.onFailure(errorCode, exception.getResult()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );
        return ResponseEntity
                .status(ErrorStatus.COMMON400.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorStatus.COMMON400, errors));
    }

    /**
     * {@code @Validated}가 붙은 컨트롤러의 파라미터 검증 실패.
     *
     * <p>본문 검증({@link MethodArgumentNotValidException})과 같은 모양의 400을 내려준다.
     * 프론트가 API마다 오류 처리를 다르게 짜지 않아도 되게 하기 위함이다.
     *
     * <p>이 핸들러가 없으면 catch-all에 걸려 500이 나간다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(ConstraintViolationException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.put(parameterNameOf(violation), violation.getMessage())
        );

        return ResponseEntity
                .status(ErrorStatus.COMMON400.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorStatus.COMMON400, errors));
    }

    /**
     * 본문 자체를 객체로 만들지 못한 경우.
     *
     * <p>enum에 없는 값이 오면 Bean Validation까지 가지도 못하고 여기서 끝난다.
     * 이 핸들러가 없으면 catch-all에 걸려 500이 나가므로, 검증 실패와 같은 모양의 400으로 맞춘다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMessageNotReadableException(HttpMessageNotReadableException exception) {
        Map<String, String> errors = new HashMap<>();

        if (exception.getCause() instanceof InvalidFormatException invalidFormatException) {
            String fieldName = fieldNameOf(invalidFormatException);
            errors.put(fieldName, describeAllowedValues(fieldName, invalidFormatException.getTargetType()));
        } else {
            errors.put(UNKNOWN_FIELD, "요청 본문의 형식이 올바르지 않습니다.");
        }

        return ResponseEntity
                .status(ErrorStatus.COMMON400.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorStatus.COMMON400, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
        log.error("예상하지 못한 예외가 발생했습니다.", exception);
        return ResponseEntity
                .status(ErrorStatus.COMMON500.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorStatus.COMMON500));
    }

    /**
     * 위반 경로는 {@code getFacilityDetail.latitude}처럼 메소드명이 앞에 붙는다. 본문 검증은
     * 필드명만 담으므로 마지막 마디만 남겨 응답 모양을 맞춘다.
     *
     * <p>파라미터 이름이 남으려면 컴파일에 {@code -parameters}가 필요하다. Spring Boot Gradle
     * 플러그인이 자동으로 넣어주므로 따로 설정하지 않는다.
     */
    private String parameterNameOf(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        int lastSeparator = propertyPath.lastIndexOf('.');

        return lastSeparator < 0 ? propertyPath : propertyPath.substring(lastSeparator + 1);
    }

    /** 중첩 객체라면 가장 안쪽 경로가 실제로 문제가 된 필드다. */
    private String fieldNameOf(InvalidFormatException exception) {
        List<JacksonException.Reference> path = exception.getPath();
        if (path.isEmpty()) {
            return UNKNOWN_FIELD;
        }

        String propertyName = path.get(path.size() - 1).getPropertyName();

        return propertyName == null ? UNKNOWN_FIELD : propertyName;
    }

    /** 허용값 목록은 enum에서 만들어낸다. 상수가 늘어도 메시지가 따라온다. */
    private String describeAllowedValues(
            String fieldName,
            Class<?> targetType
    ) {
        if (targetType == null || !targetType.isEnum()) {
            return fieldName + "의 형식이 올바르지 않습니다.";
        }

        String allowedValues = Arrays.stream(targetType.getEnumConstants())
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        return fieldName + "는 " + allowedValues + " 중 하나여야 합니다.";
    }
}
