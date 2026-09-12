package com.freepets.domain.business.dto;

import java.time.LocalDateTime;

import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.ConfidenceSource;

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

    /**
     * 매장 등록 결과. 등록이 성공하는 순간 그 계정에 사업자 프로필이 붙으므로, 앱은 이 응답을 받고
     * 계정 조회를 다시 불러 프로필 목록을 갱신하면 된다.
     *
     * @param confidence       확정된 직후라 항상 {@code CONFIRMED}다
     * @param confidenceSource 확정 근거. 사업자 본인이 적었으므로 {@code OWNER}
     */
    public record ClaimResult(
            Long facilityId,
            Confidence confidence,
            ConfidenceSource confidenceSource,
            LocalDateTime confirmedAt
    ) {}
}
