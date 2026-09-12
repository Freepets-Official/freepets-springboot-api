package com.freepets.infra.nts;

/**
 * 국세청 진위확인 결과. infra가 도메인 DTO를 모르도록 원시값으로만 담는다.
 *
 * @param valid          번호·대표자 성명·개업일자가 모두 일치하는지
 * @param validMessage   불일치 사유. 일치하면 빈 문자열이다
 * @param statusCode     사업 상태 코드. {@code 01} 계속사업자 / {@code 02} 휴업자 / {@code 03} 폐업자
 * @param statusLabel    사업 상태 문구(예: {@code 계속사업자})
 */
public record NtsValidationResult(
        boolean valid,
        String validMessage,
        String statusCode,
        String statusLabel
) {
}
