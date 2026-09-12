package com.freepets.domain.business.dto;

public class BusinessResponseDTO {

    private BusinessResponseDTO() {}

    /**
     * @param valid       진위확인 통과 여부. 불일치는 4xx로 내려가므로 200 응답에서는 항상 {@code true}다
     * @param status      사업 상태 코드. {@code 01} 계속사업자 / {@code 02} 휴업자 / {@code 03} 폐업자
     * @param statusLabel 사업 상태 문구. 화면이 사유를 그대로 보여줄 수 있게 함께 내린다
     */
    public record VerifyResult(
            boolean valid,
            String status,
            String statusLabel
    ) {}
}
