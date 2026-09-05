package com.freepets.domain.petcheck.controller;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.service.PetCheckQueryService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code GET /verify/{code}} — 동반 출입증 QR이 가리키는 공개 웹페이지.
 *
 * <p>다른 컨트롤러와 달리 {@code /api/v1} 하위가 아니고 응답도 JSON이 아니다. QR을 스캔하는
 * 시설 직원은 앱을 안 쓰므로 이 페이지 자체가 최종 화면이어야 한다 — 그래서 {@code ApiResponse}
 * 봉투 대신 렌더링된 HTML을 그대로 응답하고, 인증도 요구하지 않는다(SecurityConfig의
 * {@code /verify/**} permitAll 참고).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PetCheckVerifyController {

    private final PetCheckQueryService petCheckQueryService;

    // charset을 명시하지 않으면 text/html의 기본 컨버터 인코딩(ISO-8859-1)으로 응답이 나가
    // 한글이 깨진다 — HTML 안에 <meta charset="UTF-8">을 적어도 소용없다(그건 브라우저가
    // 바이트를 해석하는 방식이고, 실제로 어떤 바이트로 내보낼지는 이 컨버터가 정한다).
    private static final MediaType TEXT_HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    // 시설 직원이 같은 QR을 여러 번 다시 스캔하는 경우가 흔하다(재확인, 재방문객에게 다시 보여주기
    // 등) — verdict 자체는 판별 시점에 고정된 값이라 매번 JOIN FETCH로 다시 조회·렌더링할 필요가
    // 없다. 60초 정도만 캐시해도 같은 순간 몰리는 재스캔 대부분을 흡수한다.
    private static final CacheControl VERIFY_PAGE_CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    @GetMapping(value = "/verify/{code}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> verify(@PathVariable String code) {
        PetCheckResponseDTO.VerifyPage page = petCheckQueryService.getVerifyPage(code);

        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .cacheControl(VERIFY_PAGE_CACHE)
                .body(VerifyPageHtmlRenderer.render(page));
    }

    // GlobalExceptionHandler(@RestControllerAdvice)는 JSON envelope로 응답하지만, 같은
    // 컨트롤러 안의 @ExceptionHandler가 전역 것보다 우선 적용되는 걸 이용해 이 컨트롤러만
    // HTML 에러 페이지로 응답한다 — 브라우저로 직접 보는 화면에 JSON을 그대로 띄울 수는 없다.
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<String> handleNotFound(GeneralException exception) {
        return renderErrorResponse(exception.getErrorCode().getHttpStatus(), exception.getErrorCode().getMessage());
    }

    // 위 GeneralException 핸들러만 있으면 그 외의 예기치 못한 예외(NPE 등)는 이 컨트롤러를 못
    // 벗어나고 GlobalExceptionHandler의 catch-all로 넘어가 JSON으로 응답해버린다 — 이 페이지는
    // 절대 JSON을 보여주면 안 되므로 여기서도 마지막 방어선을 둔다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception exception) {
        log.error("동반 출입증 검증 페이지 렌더링 중 예상하지 못한 예외가 발생했습니다.", exception);
        return renderErrorResponse(ErrorStatus.COMMON500.getHttpStatus(), ErrorStatus.COMMON500.getMessage());
    }

    private ResponseEntity<String> renderErrorResponse(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .contentType(TEXT_HTML_UTF8)
                .body(VerifyPageHtmlRenderer.renderError(message));
    }
}
